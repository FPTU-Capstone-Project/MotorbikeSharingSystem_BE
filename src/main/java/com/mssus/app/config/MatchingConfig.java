package com.mssus.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency primitives for ride matching coordination.
 *
 * <p>Provides a dedicated task executor for long-running matching workflows and a
 * scheduled executor for driver response timeouts. Isolated pools keep the matching
 * pipeline from competing with other async tasks (email, notifications, etc.).</p>
 */
@Configuration
public class MatchingConfig {

    /**
     * Executor used for orchestrating ride matching workflows.
     */
    @Bean(name = "matchingTaskExecutor")
    public ThreadPoolTaskExecutor matchingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("matching-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return executor;
    }

    /**
     * Scheduler used to enforce driver response deadlines for ride offers.
     */
    @Bean(name = "matchingScheduler", destroyMethod = "shutdownNow")
    public ScheduledExecutorService matchingScheduler() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("matching-scheduler-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newScheduledThreadPool(4, factory);
    }
}

