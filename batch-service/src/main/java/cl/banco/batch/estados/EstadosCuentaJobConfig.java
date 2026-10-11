package cl.banco.batch.estados;

import java.nio.file.Path;
import java.sql.Types;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import cl.banco.batch.common.BatchUtils;
import cl.banco.batch.common.ConciliacionTasklet;
import cl.banco.batch.common.ExportCsvTasklet;
import cl.banco.batch.common.JobLoggerListener;
import cl.banco.batch.common.RegistroInvalidoException;
import cl.banco.batch.common.RegistroRechazadoListener;
import cl.banco.batch.config.BatchProperties;

/**
 * JOB 3 - Generacion de Estados de Cuenta Anuales (legacy: proceso anual para auditoria).
 *
 *   limpiarMovimientosStep  vacia movimiento_anual (solo en ejecucion NUEVA)
 *   cargarMovimientosStep   CSV -> validar/normalizar -> movimiento_anual (REINICIABLE desde el ultimo commit)
 *   generarEstadosStep      GROUP BY cuenta/anio -> estado_cuenta_anual (MULTI-HILO)
 *   exportarEstadosStep     estados_cuenta_anuales.csv
 *   conciliacionStep        control de integridad
 */
@Configuration
public class EstadosCuentaJobConfig {

    public static final String JOB = "estadosCuentaAnualesJob";
    public static final String STEP_CARGA = "cargarMovimientosStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager tx;
    private final BatchProperties props;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TaskExecutor executor;

    @Value("${batch.input.estados:classpath:data/estados_financieros_anuales.csv}")
    private Resource entrada;

    public EstadosCuentaJobConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                  BatchProperties props, DataSource dataSource, JdbcTemplate jdbc,
                                  @Qualifier("batchTaskExecutor") TaskExecutor executor) {
        this.jobRepository = jobRepository;
        this.tx = transactionManager;
        this.props = props;
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.executor = executor;
    }

    @Bean(name = JOB)
    public Job estadosCuentaAnualesJob() {
        return new JobBuilder(JOB, jobRepository)
                .listener(new JobLoggerListener())
                .start(limpiarMovimientosStep())
                .next(cargarMovimientosStep())
                .next(generarEstadosStep())
                .next(exportarEstadosStep())
                .next(conciliacionEstadosStep())
                .build();
    }

    // ---------------------------------------------------------------- limpiar
    @Bean
    public Step limpiarMovimientosStep() {
        return new StepBuilder("limpiarMovimientosStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    jdbc.update("DELETE FROM movimiento_anual");
                    return RepeatStatus.FINISHED;
                }, tx)
                .build();
    }

    // ---------------------------------------------------------------- cargar
    @Bean
    public FlatFileItemReader<MovimientoCsv> movimientosCsvReader() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(",");
        tokenizer.setNames("cuenta_id", "fecha", "transaccion", "monto", "descripcion");

        DefaultLineMapper<MovimientoCsv> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fs -> new MovimientoCsv(fs.readString("cuenta_id"), fs.readString("fecha"),
                fs.readString("transaccion"), fs.readString("monto"), fs.readString("descripcion")));

        FlatFileItemReader<MovimientoCsv> reader = new FlatFileItemReader<>();
        reader.setName("movimientosCsvReader"); // saveState=true: tras un fallo reanuda desde el ultimo chunk confirmado
        reader.setResource(entrada);
        reader.setEncoding("UTF-8");
        reader.setLinesToSkip(1);
        reader.setLineMapper(lineMapper);
        return reader;
    }

    @Bean
    public JdbcBatchItemWriter<MovimientoAnual> movimientosWriter() {
        return new JdbcBatchItemWriterBuilder<MovimientoAnual>()
                .dataSource(dataSource)
                .sql("INSERT INTO movimiento_anual (cuenta_id, fecha, transaccion, monto, descripcion) "
                        + "VALUES (:cuentaId, :fecha, :transaccion, :monto, :descripcion)")
                .itemSqlParameterSourceProvider(m -> new MapSqlParameterSource()
                        .addValue("cuentaId", m.cuentaId(), Types.BIGINT)
                        .addValue("fecha", BatchUtils.sqlDate(m.fecha()), Types.DATE)
                        .addValue("transaccion", m.transaccion(), Types.VARCHAR)
                        .addValue("monto", m.monto(), Types.NUMERIC)
                        .addValue("descripcion", m.descripcion(), Types.VARCHAR))
                .build();
    }

    @Bean
    public Step cargarMovimientosStep() {
        var base = new StepBuilder(STEP_CARGA, jobRepository)
                .<MovimientoCsv, MovimientoAnual>chunk(props.getChunkSize(), tx)
                .reader(movimientosCsvReader())
                .processor(new MovimientoProcessor())
                .writer(movimientosWriter());

        return BatchUtils.conTolerancia(base, props.getRetryLimit(), props.getSkipLimit(),
                        List.of(FlatFileParseException.class, RegistroInvalidoException.class))
                .listener(new RegistroRechazadoListener(jdbc, JOB, STEP_CARGA))
                .build();
    }

    // ---------------------------------------------------------------- generar estados (multi-hilo)
    @Bean
    public SynchronizedItemStreamReader<AgregadoMovimientos> agregadosReader() {
        JdbcCursorItemReader<AgregadoMovimientos> delegate = new JdbcCursorItemReaderBuilder<AgregadoMovimientos>()
                .name("agregadosCursorReader")
                .dataSource(dataSource)
                .sql("SELECT cuenta_id, CAST(EXTRACT(YEAR FROM fecha) AS INTEGER) AS anio, COUNT(*) AS cantidad, "
                        + "COALESCE(SUM(CASE WHEN transaccion = 'deposito' THEN monto ELSE 0 END), 0) AS depositos, "
                        + "COALESCE(SUM(CASE WHEN transaccion <> 'deposito' THEN monto ELSE 0 END), 0) AS egresos "
                        + "FROM movimiento_anual GROUP BY cuenta_id, CAST(EXTRACT(YEAR FROM fecha) AS INTEGER) "
                        + "ORDER BY cuenta_id, anio")
                .rowMapper((rs, i) -> new AgregadoMovimientos(rs.getLong("cuenta_id"), rs.getInt("anio"),
                        rs.getInt("cantidad"), rs.getBigDecimal("depositos"), rs.getBigDecimal("egresos")))
                .saveState(false)
                .build();

        SynchronizedItemStreamReader<AgregadoMovimientos> reader = new SynchronizedItemStreamReader<>();
        reader.setDelegate(delegate);
        return reader;
    }

    @Bean
    public JdbcBatchItemWriter<EstadoCuentaAnual> estadosWriter() {
        return new JdbcBatchItemWriterBuilder<EstadoCuentaAnual>()
                .dataSource(dataSource)
                .sql("INSERT INTO estado_cuenta_anual (cuenta_id, anio, cant_movimientos, total_depositos, total_egresos, "
                        + "saldo_neto, estado) VALUES (:cuentaId, :anio, :cant, :depositos, :egresos, :neto, :estado) "
                        + "ON CONFLICT (cuenta_id, anio) DO UPDATE SET cant_movimientos = EXCLUDED.cant_movimientos, "
                        + "total_depositos = EXCLUDED.total_depositos, total_egresos = EXCLUDED.total_egresos, "
                        + "saldo_neto = EXCLUDED.saldo_neto, estado = EXCLUDED.estado, generado_en = now()")
                .itemSqlParameterSourceProvider(e -> new MapSqlParameterSource()
                        .addValue("cuentaId", e.cuentaId(), Types.BIGINT)
                        .addValue("anio", e.anio(), Types.INTEGER)
                        .addValue("cant", e.cantidadMovimientos(), Types.INTEGER)
                        .addValue("depositos", e.totalDepositos(), Types.NUMERIC)
                        .addValue("egresos", e.totalEgresos(), Types.NUMERIC)
                        .addValue("neto", e.saldoNeto(), Types.NUMERIC)
                        .addValue("estado", e.estado(), Types.VARCHAR))
                .assertUpdates(false)
                .build();
    }

    @Bean
    public Step generarEstadosStep() {
        var base = new StepBuilder("generarEstadosStep", jobRepository)
                .<AgregadoMovimientos, EstadoCuentaAnual>chunk(props.getChunkSize(), tx)
                .reader(agregadosReader())
                .processor(new EstadoProcessor())
                .writer(estadosWriter())
                .taskExecutor(executor)
                .throttleLimit(props.getThreads());

        return BatchUtils.conTolerancia(base, props.getRetryLimit(), props.getSkipLimit(), List.of())
                .build();
    }

    // ---------------------------------------------------------------- export + conciliacion
    @Bean
    public Step exportarEstadosStep() {
        return new StepBuilder("exportarEstadosStep", jobRepository)
                .tasklet(new ExportCsvTasklet(jdbc,
                        "SELECT cuenta_id, anio, cant_movimientos, total_depositos, total_egresos, saldo_neto, estado "
                                + "FROM estado_cuenta_anual ORDER BY cuenta_id, anio",
                        Path.of(props.getOutputDir(), "estados_cuenta_anuales.csv"),
                        "cuenta_id", "anio", "cant_movimientos", "total_depositos", "total_egresos",
                        "saldo_neto", "estado"), tx)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Step conciliacionEstadosStep() {
        return new StepBuilder("conciliacionEstadosStep", jobRepository)
                .tasklet(new ConciliacionTasklet(jdbc, props.isConciliacionEstricta(), JOB, STEP_CARGA), tx)
                .build();
    }
}
