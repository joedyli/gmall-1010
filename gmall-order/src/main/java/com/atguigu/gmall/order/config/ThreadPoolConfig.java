package com.atguigu.gmall.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor(
            @Value("${thread.pool.coreSize}")Integer coreSize,
            @Value("${thread.pool.maxSize}")Integer maxSize,
            @Value("${thread.pool.keepalive}")Integer keepalive,
            @Value("${thread.pool.blockQueueSize}")Integer blockQueueSize
    ){
        return new ThreadPoolExecutor(coreSize, maxSize, keepalive, TimeUnit.SECONDS, new ArrayBlockingQueue<>(blockQueueSize));
    }
}
