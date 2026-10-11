package cl.banco.batch.common;

import java.time.LocalDate;
import java.util.List;

import org.springframework.batch.core.step.builder.FaultTolerantStepBuilder;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.CannotCreateTransactionException;

public final class BatchUtils {

    /** Fallos TEMPORALES (caida de conexion, deadlock, timeout): se reintentan con espera exponencial. */
    private static final List<Class<? extends Throwable>> REINTENTABLES = List.of(
            TransientDataAccessException.class,
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class,
            CannotGetJdbcConnectionException.class);

    private BatchUtils() {
    }

    /**
     * Aplica la politica de tolerancia a fallos comun a todos los jobs:
     *  - RETRY (n intentos, backoff exponencial) ante fallos transitorios de base de datos
     *  - SKIP de los registros invalidos (hasta skipLimit; sobre ese umbral el step falla)
     */
    public static <I, O> FaultTolerantStepBuilder<I, O> conTolerancia(
            SimpleStepBuilder<I, O> builder, int retryLimit, int skipLimit,
            List<Class<? extends Throwable>> omitibles) {

        FaultTolerantStepBuilder<I, O> ft = builder
                .faultTolerant()
                .retryLimit(retryLimit)
                .backOffPolicy(backOff())
                .skipLimit(skipLimit);
        for (Class<? extends Throwable> r : REINTENTABLES) {
            ft = ft.retry(r);
        }
        for (Class<? extends Throwable> s : omitibles) {
            ft = ft.skip(s);
        }
        return ft;
    }

    public static BackOffPolicy backOff() {
        ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
        policy.setInitialInterval(250);
        policy.setMultiplier(2.0);
        policy.setMaxInterval(4000);
        return policy;
    }

    public static java.sql.Date sqlDate(LocalDate fecha) {
        return fecha == null ? null : java.sql.Date.valueOf(fecha);
    }
}
