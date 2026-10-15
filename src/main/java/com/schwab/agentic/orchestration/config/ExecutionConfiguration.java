package com.schwab.agentic.orchestration.config;

import com.schwab.agentic.orchestration.engine.RetryPolicy;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutionConfiguration {

    @Bean(destroyMethod = "shutdown")
    ExecutorService taskExecutor(
            @Value("${orchestration.execution.max-parallel-tasks:4}") int poolSize,
            @Value("${orchestration.execution.queue-capacity:64}") int queueCapacity) {
        if (poolSize < 1 || poolSize > 64 || queueCapacity < 1) {
            throw new IllegalArgumentException("Task executor size must be 1–64 and queue capacity must be positive");
        }
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "agent-task-" + threadNumber.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    RetryPolicy retryPolicy(
            @Value("${orchestration.retry.max-attempts:3}") int maxAttempts,
            @Value("${orchestration.retry.initial-delay:100ms}") Duration initialDelay,
            @Value("${orchestration.retry.maximum-delay:1s}") Duration maximumDelay,
            @Value("${orchestration.retry.multiplier:2.0}") double multiplier) {
        return new RetryPolicy(maxAttempts, initialDelay, maximumDelay, multiplier);
    }
}
