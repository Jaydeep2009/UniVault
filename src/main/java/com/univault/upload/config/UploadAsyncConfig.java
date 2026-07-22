package com.univault.upload.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded thread pool dedicated to chunk uploads, separate from Tomcat's
 * request-handling threads.
 *
 * Sizing note: worst-case in-flight memory ≈ pool-size × chunk-size. With
 * the default 4MB chunk size (univault.chunk.size-bytes) and 4 threads,
 * that's a ~16MB ceiling — this is the answer to the byte[] vs InputStream
 * question: bound concurrency instead of switching the shared
 * StorageProvider interface to streaming. Tune parallelism, not chunk type.
 */
@Configuration
public class UploadAsyncConfig {

    @Bean(name = "chunkUploadExecutor")
    public Executor chunkUploadExecutor(
            @Value("${univault.upload.parallelism:4}") int parallelism) {

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "chunk-upload-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };

        return Executors.newFixedThreadPool(parallelism, threadFactory);
    }
}