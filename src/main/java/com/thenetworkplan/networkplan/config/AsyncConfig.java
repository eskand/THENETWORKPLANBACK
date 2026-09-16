package com.thenetworkplan.networkplan.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Java 21 virtual threads.
 *
 * <p>{@code spring.threads.virtual.enabled=true} already puts every HTTP request
 * and every {@code @Async} task on a virtual thread. This executor is the one the
 * dispatch board fans out on: the board is six independent database reads, and on
 * virtual threads each blocking JDBC call parks its own carrier-free thread
 * instead of holding a pooled platform thread, so the six run concurrently at the
 * cost of one.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String DISPATCH_EXECUTOR = "dispatchExecutor";

    @Bean(name = DISPATCH_EXECUTOR, destroyMethod = "close")
    public ExecutorService dispatchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
