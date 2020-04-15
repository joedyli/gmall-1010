package com.atguigu.gmall.index.config;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * joinPoint.getArgs(); 获取方法参数
     * (MethodSignature)joinPoint.getSignature()：获取方法签名
     * joinPoint.getTarget().getClass()：获取目标方法所在的类
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("@annotation(com.atguigu.gmall.index.config.GmallCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 通过方法签名获取缓存注解对象
        GmallCache cache = signature.getMethod().getAnnotation(GmallCache.class);
        // 获取缓存前缀
        String prefix = cache.prefix();
        // 获取方法参数
        String args = Arrays.asList(joinPoint.getArgs()).toString();
        // 获取方法的返回值类型
        Class returnType = signature.getReturnType();

        // 组装缓存的key
        String key = prefix + args;

        // 查询缓存，判断缓存中是否存在
        String json = this.redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseObject(json, returnType);
        }

        // 缓存中没有，加分布式锁
        String lock = cache.lock();
        RLock fairLock = this.redissonClient.getFairLock(lock + args);
        fairLock.lock();

        // 查询缓存，判断缓存中是否存在
        String json2 = this.redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(json2)){
            // 返回之前，先释放锁
            fairLock.unlock();
            return JSON.parseObject(json2, returnType);
        }

        // 执行目标方法
        Object result = joinPoint.proceed(joinPoint.getArgs());

        // 放入缓存
        int timeout = cache.timeout();
        int random = cache.random();
        this.redisTemplate.opsForValue().set(key, JSON.toJSONString(result), timeout + new Random().nextInt(random), TimeUnit.MINUTES);

        // 释放分布式锁
        fairLock.unlock();

        return result;
    }
}
