package uk.ac.ebi.protvar;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {
    // Download partitions and upload-build chunks share one DB budget: HeavyDbWork (app.db-work.*).

    @Bean
    public AsyncTaskExecutor inputExecutor() {
        // after an upload: cache the lines, detect the assembly, start the filter build (InputService)
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setThreadNamePrefix("input-");
        executor.initialize();
        return executor;
    }

    @Bean
    public AsyncTaskExecutor uploadFilterExecutor() {
        // background builds of large uploads for filter/sort (UploadFilterService)
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("upload-filter-");
        executor.initialize();
        return executor;
    }
}
