package com.atguigu.gmall.wms.config;

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
        arguments.put("x-message-ttl", 90000);
        arguments.put("x-dead-letter-exchange", "order-exchange");
        arguments.put("x-dead-letter-routing-key", "stock.unlock");
        return new Queue("stock-ttl-queue", true, false, false, arguments);
    }

    // 把延时队列绑定到交换机
    @Bean
    public Binding ttlBinding(){

        return new Binding("stock-ttl-queue", Binding.DestinationType.QUEUE, "order-exchange", "stock.ttl", null);
    }

    // 声明死信交换机：order-exchange

    // 声明死信队列：order-stock-queue

    // 把死信队列绑定到死信交换机：注解中已绑定
}
