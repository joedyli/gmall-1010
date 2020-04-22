package com.atguigu.gmall.oms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;


@Configuration
public class RabbitMqConfig {

    // 声明延时交换机：order-exchange

    // 声明延时队列
    @Bean
    public Queue ttlQueue(){
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", 60000);
        arguments.put("x-dead-letter-exchange", "order-exchange");
        arguments.put("x-dead-letter-routing-key", "order.dead");
        return new Queue("order-ttl-queue", true, false, false, arguments);
    }

    // 把延时队列绑定到交换机
    @Bean
    public Binding ttlBinding(){

        return new Binding("order-ttl-queue", Binding.DestinationType.QUEUE, "order-exchange", "order.ttl", null);
    }

    // 声明死信交换机：order-exchange

    // 声明死信队列
    @Bean
    public Queue deadQueue(){
        return new Queue("order-dead-queue", true, false, false);
    }

    // 把死信队列绑定到死信交换机
    @Bean
    public Binding binding(){
        return new Binding("order-dead-queue", Binding.DestinationType.QUEUE, "order-exchange", "order.dead", null);
    }
}
