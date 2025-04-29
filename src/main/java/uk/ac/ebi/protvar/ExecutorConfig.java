package uk.ac.ebi.protvar;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    @Bean
    public TaskExecutor downloadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // number of parallel jobs
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);   // buffer queue before rejecting
        executor.setThreadNamePrefix("download-task-");
        executor.initialize();
        return executor;
    }

}
