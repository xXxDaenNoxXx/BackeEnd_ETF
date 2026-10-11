package cl.banco.batch.config;

import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BatchInfraConfig {

    /** Pool para steps multi-hilo y particiones (paralelismo dentro de un job). */
    @Bean(name = "batchTaskExecutor")
    public TaskExecutor batchTaskExecutor(BatchProperties props) {
        int hilos = Math.max(props.getThreads(), props.getPartitions());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(hilos);
        executor.setMaxPoolSize(hilos);
        executor.setThreadNamePrefix("batch-worker-");
        executor.initialize();
        return executor;
    }

    /**
     * Launcher ASINCRONO: la API REST y el scheduler devuelven de inmediato y el job corre en su propio hilo.
     * (Se llama distinto de "jobLauncher" para no pisar el bean que ya define Spring Boot.)
     */
    @Bean(name = "asyncJobLauncher")
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-job-"));
        launcher.afterPropertiesSet();
        return launcher;
    }
}
