package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class StockListener {

    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "stock:lock:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "order-stock-queue", durable = "true"),
            exchange = @Exchange(value = "order-exchange", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"stock.unlock"}
    ))
    public void listener(String orderToken, Channel channel, Message message) throws IOException {

        try {
            // 获取redis中该订单的锁定库存信息
            String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
            if (StringUtils.isNotBlank(json)){
                // 反序列化获取库存的锁定信息
                List<SkuLockVo> skuLockVos = JSON.parseArray(json, SkuLockVo.class);
                // 遍历并解锁库存信息
                skuLockVos.forEach(skuLockVo -> {
                    this.wareSkuMapper.unlock(skuLockVo.getWareSkuId(), skuLockVo.getCount());
                });
                // 删除redis中库存锁定信息
                this.redisTemplate.delete(KEY_PREFIX + orderToken);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "stock-minus-queue", durable = "true"),
            exchange = @Exchange(value = "order-exchange", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"stock.minus"}
    ))
    public void minusStock(String orderToken, Channel channel, Message message) throws IOException {

        try {
            // 获取redis中该订单的锁定库存信息
            String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
            if (StringUtils.isNotBlank(json)){
                // 反序列化获取库存的锁定信息
                List<SkuLockVo> skuLockVos = JSON.parseArray(json, SkuLockVo.class);
                // 遍历并解锁库存信息
                skuLockVos.forEach(skuLockVo -> {
                    this.wareSkuMapper.minus(skuLockVo.getWareSkuId(), skuLockVo.getCount());
                });
                // 删除redis中库存锁定信息
                this.redisTemplate.delete(KEY_PREFIX + orderToken);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }
}
