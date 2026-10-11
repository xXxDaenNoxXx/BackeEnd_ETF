package cl.banco.batch.service;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import cl.banco.batch.estados.EstadosCuentaJobConfig;
import cl.banco.batch.intereses.InteresesJobConfig;
import cl.banco.batch.transacciones.TransaccionesJobConfig;

/** Lanza jobs por alias corto (transacciones | intereses | estados). Cada lanzamiento es una instancia NUEVA. */
@Service
public class JobRunner {

    /** alias -> nombre real del job */
    public static final Map<String, String> ALIAS = new LinkedHashMap<>();

    static {
        ALIAS.put("transacciones", TransaccionesJobConfig.JOB);
        ALIAS.put("intereses", InteresesJobConfig.JOB);
        ALIAS.put("estados", EstadosCuentaJobConfig.JOB);
    }

    private final JobLauncher jobLauncher;
    private final Map<String, Job> jobs; // todos los beans Job, por nombre de bean

    public JobRunner(@Qualifier("asyncJobLauncher") JobLauncher jobLauncher, Map<String, Job> jobs) {
        this.jobLauncher = jobLauncher;
        this.jobs = jobs;
    }

    public JobExecution lanzar(String alias) throws Exception {
        String nombre = ALIAS.get(alias);
        if (nombre == null) {
            throw new IllegalArgumentException("Job desconocido: " + alias + " (validos: " + ALIAS.keySet() + ")");
        }
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())          // asegura una instancia nueva por lanzamiento
                .addString("periodo", YearMonth.now().toString())        // AAAA-MM, lo usa el calculo de intereses
                .toJobParameters();
        return jobLauncher.run(buscar(nombre), params);
    }

    public Job buscar(String nombreJob) {
        return jobs.values().stream()
                .filter(j -> j.getName().equals(nombreJob))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Job no registrado: " + nombreJob));
    }

    public JobLauncher getJobLauncher() {
        return jobLauncher;
    }
}
