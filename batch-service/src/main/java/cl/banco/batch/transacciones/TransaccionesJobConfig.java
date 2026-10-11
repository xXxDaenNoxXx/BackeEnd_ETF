package cl.banco.batch.transacciones;

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
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
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
import cl.banco.batch.common.RegistroRechazadoListener;
import cl.banco.batch.config.BatchProperties;

/**
 * JOB 1 - Reporte de Transacciones Diarias (legacy: script COBOL nocturno).
 *
 *   cargarTransaccionesStep  CSV -> validar/detectar anomalias -> tabla (MULTI-HILO, chunk, retry + skip)
 *   resumenTransaccionesStep totales por dia (creditos, debitos, anomalias)
 *   exportarResumenStep      reporte_transacciones_diarias.csv
 *   exportarAnomaliasStep    anomalias_transacciones.csv
 *   conciliacionStep         control de integridad leidos = escritos + omitidos
 */
@Configuration
public class TransaccionesJobConfig {

    public static final String JOB = "reporteTransaccionesDiariasJob";
    public static final String STEP_CARGA = "cargarTransaccionesStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager tx;
    private final BatchProperties props;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TaskExecutor executor;

    @Value("${batch.input.transacciones:classpath:data/movimientos_financieros_diarios.csv}")
    private Resource entrada;

    public TransaccionesJobConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager,
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
    public Job reporteTransaccionesDiariasJob() {
        return new JobBuilder(JOB, jobRepository)
                .listener(new JobLoggerListener())
                .start(cargarTransaccionesStep())
                .next(resumenTransaccionesStep())
                .next(exportarResumenStep())
                .next(exportarAnomaliasStep())
                .next(conciliacionTransaccionesStep())
                .build();
    }

    // ---------------------------------------------------------------- lectura / escritura
    @Bean
    public SynchronizedItemStreamReader<TransaccionCsv> transaccionesReader() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(",");
        tokenizer.setNames("id", "fecha", "monto", "tipo");

        DefaultLineMapper<TransaccionCsv> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fs -> new TransaccionCsv(
                fs.readString("id"), fs.readString("fecha"), fs.readString("monto"), fs.readString("tipo")));

        FlatFileItemReader<TransaccionCsv> delegate = new FlatFileItemReader<>();
        delegate.setName("transaccionesCsvReader");
        delegate.setResource(entrada);
        delegate.setEncoding("UTF-8");
        delegate.setLinesToSkip(1); // encabezado
        delegate.setLineMapper(lineMapper);
        delegate.setSaveState(false); // step multi-hilo: sin estado de reinicio; el UPSERT lo hace idempotente

        // FlatFileItemReader NO es thread-safe: el wrapper serializa solo la lectura, el proceso y la escritura corren en paralelo
        SynchronizedItemStreamReader<TransaccionCsv> reader = new SynchronizedItemStreamReader<>();
        reader.setDelegate(delegate);
        return reader;
    }

    @Bean
    public JdbcBatchItemWriter<TransaccionProcesada> transaccionesWriter() {
        return new JdbcBatchItemWriterBuilder<TransaccionProcesada>()
                .dataSource(dataSource)
                .sql("INSERT INTO transaccion_procesada (id, fecha, monto, tipo, estado, motivo_anomalia) "
                        + "VALUES (:id, :fecha, :monto, :tipo, :estado, :motivo) "
                        + "ON CONFLICT (id) DO UPDATE SET fecha = EXCLUDED.fecha, monto = EXCLUDED.monto, "
                        + "tipo = EXCLUDED.tipo, estado = EXCLUDED.estado, "
                        + "motivo_anomalia = EXCLUDED.motivo_anomalia, procesado_en = now()")
                .itemSqlParameterSourceProvider(t -> new MapSqlParameterSource()
                        .addValue("id", t.id(), Types.BIGINT)
                        .addValue("fecha", BatchUtils.sqlDate(t.fecha()), Types.DATE)
                        .addValue("monto", t.monto(), Types.NUMERIC)
                        .addValue("tipo", t.tipo(), Types.VARCHAR)
                        .addValue("estado", t.estado(), Types.VARCHAR)
                        .addValue("motivo", t.motivoAnomalia(), Types.VARCHAR))
                .assertUpdates(false)
                .build();
    }

    // ---------------------------------------------------------------- steps
    @Bean
    public Step cargarTransaccionesStep() {
        var base = new StepBuilder(STEP_CARGA, jobRepository)
                .<TransaccionCsv, TransaccionProcesada>chunk(props.getChunkSize(), tx)
                .reader(transaccionesReader())
                .processor(new TransaccionProcessor())
                .writer(transaccionesWriter())
                .taskExecutor(executor)      // paralelismo: varios chunks a la vez
                .throttleLimit(props.getThreads());

        return BatchUtils.conTolerancia(base, props.getRetryLimit(), props.getSkipLimit(),
                        List.of(FlatFileParseException.class, NumberFormatException.class))
                .listener(new RegistroRechazadoListener(jdbc, JOB, STEP_CARGA))
                .build();
    }

    @Bean
    public Step resumenTransaccionesStep() {
        return new StepBuilder("resumenTransaccionesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // Reconstruye el resumen completo desde el detalle: idempotente ante reejecuciones
                    jdbc.update("DELETE FROM resumen_transacciones_diarias");
                    int dias = jdbc.update(
                            "INSERT INTO resumen_transacciones_diarias "
                                    + "(fecha, total_creditos, total_debitos, cant_transacciones, cant_anomalias) "
                                    + "SELECT fecha, "
                                    + "COALESCE(SUM(CASE WHEN estado = 'VALIDA' AND tipo = 'credito' THEN monto END), 0), "
                                    + "COALESCE(SUM(CASE WHEN estado = 'VALIDA' AND tipo = 'debito' THEN monto END), 0), "
                                    + "COUNT(*), "
                                    + "COUNT(CASE WHEN estado = 'ANOMALA' THEN 1 END) "
                                    + "FROM transaccion_procesada WHERE fecha IS NOT NULL GROUP BY fecha");
                    contribution.incrementWriteCount(dias);
                    return RepeatStatus.FINISHED;
                }, tx)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Step exportarResumenStep() {
        return new StepBuilder("exportarResumenStep", jobRepository)
                .tasklet(new ExportCsvTasklet(jdbc,
                        "SELECT fecha, total_creditos, total_debitos, "
                                + "(total_creditos - total_debitos) AS balance_neto, "
                                + "cant_transacciones, cant_anomalias "
                                + "FROM resumen_transacciones_diarias ORDER BY fecha",
                        Path.of(props.getOutputDir(), "reporte_transacciones_diarias.csv"),
                        "fecha", "total_creditos", "total_debitos", "balance_neto",
                        "cant_transacciones", "cant_anomalias"), tx)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Step exportarAnomaliasStep() {
        return new StepBuilder("exportarAnomaliasStep", jobRepository)
                .tasklet(new ExportCsvTasklet(jdbc,
                        "SELECT id, fecha, monto, tipo, motivo_anomalia FROM transaccion_procesada "
                                + "WHERE estado = 'ANOMALA' ORDER BY id",
                        Path.of(props.getOutputDir(), "anomalias_transacciones.csv"),
                        "id", "fecha", "monto", "tipo", "motivo_anomalia"), tx)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Step conciliacionTransaccionesStep() {
        return new StepBuilder("conciliacionTransaccionesStep", jobRepository)
                .tasklet(new ConciliacionTasklet(jdbc, props.isConciliacionEstricta(), JOB, STEP_CARGA), tx)
                .build();
    }
}
