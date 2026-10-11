package cl.banco.batch.intereses;

import java.nio.file.Path;
import java.sql.Types;
import java.time.YearMonth;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
 * JOB 2 - Calculo de Intereses Mensuales (legacy: script Shell + COBOL mensual).
 *
 *   limpiarStagingStep        vacia la tabla de trabajo (solo en una ejecucion NUEVA; en un restart no se repite)
 *   cargarCuentasStep         CSV -> validar -> cuenta_staging (retry + skip, deduplica por cuenta_id)
 *   calcularInteresesManager  PARTICIONADO por rango de cuenta_id; cada particion calcula en su propio hilo
 *   exportarInteresesStep     intereses_mensuales.csv
 *   conciliacionStep          control de integridad
 */
@Configuration
public class InteresesJobConfig {

    public static final String JOB = "calculoInteresesMensualesJob";
    public static final String STEP_CARGA = "cargarCuentasStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager tx;
    private final BatchProperties props;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TaskExecutor executor;

    @Value("${batch.input.intereses:classpath:data/intereses_trimestrales.csv}")
    private Resource entrada;

    public InteresesJobConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager,
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
    public Job calculoInteresesMensualesJob() {
        return new JobBuilder(JOB, jobRepository)
                .listener(new JobLoggerListener())
                .start(limpiarStagingStep())
                .next(cargarCuentasStep())
                .next(calcularInteresesManagerStep())
                .next(exportarInteresesStep())
                .next(conciliacionInteresesStep())
                .build();
    }

    // ---------------------------------------------------------------- step 1: limpiar
    @Bean
    public Step limpiarStagingStep() {
        return new StepBuilder("limpiarStagingStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    jdbc.update("DELETE FROM cuenta_staging");
                    return RepeatStatus.FINISHED;
                }, tx)
                .build();
    }

    // ---------------------------------------------------------------- step 2: cargar y validar
    @Bean
    public FlatFileItemReader<CuentaCsv> cuentasCsvReader() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(",");
        tokenizer.setNames("cuenta_id", "nombre", "saldo", "edad", "tipo");

        DefaultLineMapper<CuentaCsv> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fs -> new CuentaCsv(fs.readString("cuenta_id"), fs.readString("nombre"),
                fs.readString("saldo"), fs.readString("edad"), fs.readString("tipo")));

        FlatFileItemReader<CuentaCsv> reader = new FlatFileItemReader<>();
        reader.setName("cuentasCsvReader"); // con saveState=true (por defecto) el step puede reanudarse tras un fallo
        reader.setResource(entrada);
        reader.setEncoding("UTF-8");
        reader.setLinesToSkip(1);
        reader.setLineMapper(lineMapper);
        return reader;
    }

    @Bean
    public JdbcBatchItemWriter<CuentaStaging> cuentasStagingWriter() {
        return new JdbcBatchItemWriterBuilder<CuentaStaging>()
                .dataSource(dataSource)
                .sql("INSERT INTO cuenta_staging (cuenta_id, nombre, saldo, edad, tipo) "
                        + "VALUES (:cuentaId, :nombre, :saldo, :edad, :tipo) ON CONFLICT (cuenta_id) DO NOTHING")
                .itemSqlParameterSourceProvider(c -> new MapSqlParameterSource()
                        .addValue("cuentaId", c.cuentaId(), Types.BIGINT)
                        .addValue("nombre", c.nombre(), Types.VARCHAR)
                        .addValue("saldo", c.saldo(), Types.NUMERIC)
                        .addValue("edad", c.edad(), Types.INTEGER)
                        .addValue("tipo", c.tipo(), Types.VARCHAR))
                .assertUpdates(false) // DO NOTHING (cuenta duplicada) devuelve 0 filas y NO es un error
                .build();
    }

    @Bean
    public Step cargarCuentasStep() {
        var base = new StepBuilder(STEP_CARGA, jobRepository)
                .<CuentaCsv, CuentaStaging>chunk(props.getChunkSize(), tx)
                .reader(cuentasCsvReader())
                .processor(new CuentaProcessor())
                .writer(cuentasStagingWriter());

        return BatchUtils.conTolerancia(base, props.getRetryLimit(), props.getSkipLimit(),
                        List.of(FlatFileParseException.class, RegistroInvalidoException.class))
                .listener(new RegistroRechazadoListener(jdbc, JOB, STEP_CARGA))
                .build();
    }

    // ---------------------------------------------------------------- step 3: calculo particionado
    @Bean
    @StepScope
    public JdbcCursorItemReader<CuentaStaging> cuentasStagingReader(
            @Value("#{stepExecutionContext['minValue']}") Long minValue,
            @Value("#{stepExecutionContext['maxValue']}") Long maxValue) {
        return new JdbcCursorItemReaderBuilder<CuentaStaging>()
                .name("cuentasStagingReader")
                .dataSource(dataSource)
                .sql("SELECT cuenta_id, nombre, saldo, edad, tipo FROM cuenta_staging "
                        + "WHERE cuenta_id BETWEEN ? AND ? ORDER BY cuenta_id")
                .preparedStatementSetter(ps -> {
                    ps.setLong(1, minValue);
                    ps.setLong(2, maxValue);
                })
                .rowMapper((rs, i) -> new CuentaStaging(rs.getLong("cuenta_id"), rs.getString("nombre"),
                        rs.getBigDecimal("saldo"), rs.getInt("edad"), rs.getString("tipo")))
                .build();
    }

    @Bean
    @StepScope
    public InteresProcessor interesProcessor(@Value("#{jobParameters['periodo']}") String periodo) {
        String p = (periodo == null || periodo.isBlank()) ? YearMonth.now().toString() : periodo;
        return new InteresProcessor(props.getInteres(), p);
    }

    @Bean
    public JdbcBatchItemWriter<InteresMensual> interesWriter() {
        return new JdbcBatchItemWriterBuilder<InteresMensual>()
                .dataSource(dataSource)
                .sql("INSERT INTO interes_mensual (cuenta_id, periodo, nombre, tipo, saldo, tasa_mensual, interes, nuevo_saldo) "
                        + "VALUES (:cuentaId, :periodo, :nombre, :tipo, :saldo, :tasa, :interes, :nuevoSaldo) "
                        + "ON CONFLICT (cuenta_id, periodo) DO UPDATE SET nombre = EXCLUDED.nombre, tipo = EXCLUDED.tipo, "
                        + "saldo = EXCLUDED.saldo, tasa_mensual = EXCLUDED.tasa_mensual, interes = EXCLUDED.interes, "
                        + "nuevo_saldo = EXCLUDED.nuevo_saldo, calculado_en = now()")
                .itemSqlParameterSourceProvider(i -> new MapSqlParameterSource()
                        .addValue("cuentaId", i.cuentaId(), Types.BIGINT)
                        .addValue("periodo", i.periodo(), Types.VARCHAR)
                        .addValue("nombre", i.nombre(), Types.VARCHAR)
                        .addValue("tipo", i.tipo(), Types.VARCHAR)
                        .addValue("saldo", i.saldo(), Types.NUMERIC)
                        .addValue("tasa", i.tasaMensual(), Types.NUMERIC)
                        .addValue("interes", i.interes(), Types.NUMERIC)
                        .addValue("nuevoSaldo", i.nuevoSaldo(), Types.NUMERIC))
                .assertUpdates(false)
                .build();
    }

    /** Step que ejecuta CADA particion (un rango de cuentas). */
    @Bean
    public Step calcularInteresesWorkerStep() {
        var base = new StepBuilder("calcularInteresesWorkerStep", jobRepository)
                .<CuentaStaging, InteresMensual>chunk(props.getChunkSize(), tx)
                .reader(cuentasStagingReader(null, null))   // los argumentos reales los inyecta el scope "step"
                .processor(interesProcessor(null))
                .writer(interesWriter());

        return BatchUtils.conTolerancia(base, props.getRetryLimit(), props.getSkipLimit(), List.of())
                .build();
    }

    /** Step "manager": crea las particiones y reparte el trabajo entre los hilos del pool. */
    @Bean
    public Step calcularInteresesManagerStep() {
        return new StepBuilder("calcularInteresesManagerStep", jobRepository)
                .partitioner("calcularInteresesWorkerStep", new RangePartitioner(jdbc))
                .step(calcularInteresesWorkerStep())
                .gridSize(props.getPartitions())
                .taskExecutor(executor)
                .build();
    }

    // ---------------------------------------------------------------- steps 4 y 5
    @Bean
    public Step exportarInteresesStep() {
        return new StepBuilder("exportarInteresesStep", jobRepository)
                .tasklet(new ExportCsvTasklet(jdbc,
                        "SELECT cuenta_id, periodo, nombre, tipo, saldo, tasa_mensual, interes, nuevo_saldo "
                                + "FROM interes_mensual ORDER BY periodo, cuenta_id",
                        Path.of(props.getOutputDir(), "intereses_mensuales.csv"),
                        "cuenta_id", "periodo", "nombre", "tipo", "saldo", "tasa_mensual", "interes", "nuevo_saldo"), tx)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Step conciliacionInteresesStep() {
        return new StepBuilder("conciliacionInteresesStep", jobRepository)
                .tasklet(new ConciliacionTasklet(jdbc, props.isConciliacionEstricta(), JOB, STEP_CARGA), tx)
                .build();
    }
}
