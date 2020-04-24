package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String KEY_PREFIX = "stock:lock:";

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public List<SkuLockVo> checkAndLock(List<SkuLockVo> skuLockVos) {

        // 判断集合是否为空
        if (CollectionUtils.isEmpty(skuLockVos)) {
            return null;
        }

        // 遍历验库存并锁库存
        skuLockVos.forEach(skuLockVo -> {
            checkLock(skuLockVo);
        });

        // 判断集合中有一个商品锁定失败，锁定成功的那些商品要回滚
        if(skuLockVos.stream().anyMatch(skuLockVo -> skuLockVo.getLock()==false)){
            skuLockVos.stream().filter(SkuLockVo::getLock).collect(Collectors.toList()).forEach(skuLockVo -> {
                this.wareSkuMapper.unlock(skuLockVo.getWareSkuId(), skuLockVo.getCount());
            });
            return skuLockVos;
        }

        // 如果都锁定成功，应该把锁定状态保存到redis中（orderToken作为key，以锁定信息作为value）
        String orderToken = skuLockVos.get(0).getOrderToken();
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, JSON.toJSONString(skuLockVos));

        this.rabbitTemplate.convertAndSend("order-exchange", "stock.ttl", orderToken);

        return null;
    }

    private void checkLock(SkuLockVo skuLockVo){
        RLock lock = this.redissonClient.getFairLock("lock:" + skuLockVo.getSkuId());
        lock.lock();
        // 验库存，本质就是查询
        List<WareSkuEntity> wareSkuEntities = this.wareSkuMapper.check(skuLockVo.getSkuId(), skuLockVo.getCount());
        if (CollectionUtils.isEmpty(wareSkuEntities)) {
            skuLockVo.setLock(false); // 如果没有一个仓库满足购买数量，即锁定失败
            // 释放锁
            lock.unlock();
            return;
        }

        // 锁库存，本质就是更新
        // 这里为了方便，取第一条库存信息，来锁库存
        WareSkuEntity wareSkuEntity = wareSkuEntities.get(0);
        if(this.wareSkuMapper.lock(wareSkuEntity.getId(), skuLockVo.getCount()) == 1){
            skuLockVo.setLock(true);
            skuLockVo.setWareSkuId(wareSkuEntity.getId());
        } else {
            skuLockVo.setLock(false);
        }

        lock.unlock();
    }

}
