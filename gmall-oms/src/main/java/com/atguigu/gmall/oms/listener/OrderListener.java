package com.atguigu.gmall.oms.listener;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderListener {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = {"order-dead-queue"})
    public void closeOrder(String orderToken, Channel channel, Message message) throws IOException {

        try {
            // 更新订单状态，更新为4
            // 执行关单操作，如果返回值是1，说明执行关单成功，再去执行解锁库存的操作；如果返回值是0，是说明执行关单失败
            if(this.orderMapper.closeOrder(orderToken) == 1){
                // 解锁库存
                this.rabbitTemplate.convertAndSend("order-exchange", "stock.unlock", orderToken);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e){
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "order-pay-queue", durable = "true"),
            exchange = @Exchange(value = "order-exchange", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"pay.success"}
    ))
    public void paySuccess(String orderToken, Channel channel, Message message) throws IOException {

        try {
            // 更新订单状态
            if (this.orderMapper.successOrder(orderToken) == 1) {
                // 减库存
                this.rabbitTemplate.convertAndSend("order-exchange", "stock.minus", orderToken);

                // 给用户加积分 TODO ：给ums发送消息，消息内容：userId 订单购买积分 和 成长积分

            } else {
                // 退款 TODO
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }
}
