package com.atguigu.gmall.oms.listener;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderListener {

    @RabbitListener(queues = {"order-dead-queue"})
    public void closeOrder(String orderToken, Channel channel, Message message) throws IOException {

        System.out.println("orderToken: " + orderToken);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
