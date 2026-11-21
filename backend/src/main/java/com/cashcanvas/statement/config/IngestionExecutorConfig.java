package com.cashcanvas.statement.config;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.cashcanvas.common.logging.MdcTaskDecorator;

/**
 * Bounded pool for statement ingestion.
 *
 * How ThreadPoolExecutor fills up (defaults: core 2, queue 10, max 4):
 *   jobs 1-2   -> run immediately on the 2 core threads
 *   jobs 3-12  -> wait in the queue
 *   jobs 13-14 -> extra threads are created (up to max 4)
 *   job 15+    -> rejected: TaskRejectedException is thrown to the caller -> HTTP 429
 */
@Configuration
@EnableAsync
public class IngestionExecutorConfig {

    public static final String INGESTION_EXECUTOR = "ingestionExecutor";

    @Bean(name = INGESTION_EXECUTOR)
    public ThreadPoolTaskExecutor ingestionExecutor(UploadProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("ingest-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        // On shutdown, let running jobs finish (up to 30s) instead of killing them mid-insert.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /** Thread-safe; one instance is enough. */
    @Bean
    public Tika tika() {
        return new Tika();
    }
}
