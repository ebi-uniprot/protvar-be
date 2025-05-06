package uk.ac.ebi.protvar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorConfig.class);
    @Bean
    public TaskExecutor downloadJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // Parallel download jobs
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);   // Buffer jobs during load
        executor.setThreadNamePrefix("download-job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public AsyncTaskExecutor partitionProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Parallelism for partition processing
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50); // Limit on queued tasks
        executor.setThreadNamePrefix("partition-task-");
        executor.setRejectedExecutionHandler((r, e) -> {
            LOGGER.error("Partition task rejected: {}", r.toString());
            throw new RejectedExecutionException("Partition executor saturated");
        });
        executor.initialize();
        return executor;
    }
}
