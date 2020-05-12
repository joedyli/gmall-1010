package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final String KEY_PREFIX = "index:cates:";

    public List<CategoryEntity> queryLvl1Categories() {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0l);
        return listResponseVo.getData();
    }

    public List<CategoryEntity> queryLvl2CategoriesWithSub(Long pid) {

        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesWithSub(pid);
        return listResponseVo.getData();
    }

    @GmallCache(prefix = KEY_PREFIX, timeout = 43200, lock = "lock", random = 4320)
    public List<CategoryEntity> queryLvl2CategoriesWithSub2(Long pid) {
        // 缓存中没有
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(pid);
        List<CategoryEntity> categories = listResponseVo.getData();
        return categories;
    }

    /*public List<CategoryVo> queryLvl2CategoriesWithSub2(Long pid) {

        // 查询缓存，如果缓存中有，直接返回
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseArray(json, CategoryVo.class);
        }

        RLock lock = this.redissonClient.getLock("lock" + pid);
        lock.lock();

        String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json2)){
            return JSON.parseArray(json2, CategoryVo.class);
        }

        // 缓存中没有
        ResponseVo<List<CategoryVo>> listResponseVo = this.pmsClient.queryCategoryVoByPid(pid);
        List<CategoryVo> categoryVos = listResponseVo.getData();

        if (CollectionUtils.isEmpty(categoryVos)){
            // 如果查询数据为null，也要放入缓存，缓存时间3min
            this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryVos), 3, TimeUnit.MINUTES);
        } else {
            // 如果数据存在，放入缓存，缓存时间30天
            this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryVos), 43200 + new Random().nextInt(4320), TimeUnit.MINUTES);
        }

        lock.unlock();

        return categoryVos;
    }*/

    public void testLock() {

        // 获取锁
        RLock lock = this.redissonClient.getLock("lock");

        // 加锁
        lock.lock(20, TimeUnit.SECONDS);

        String numString = this.redisTemplate.opsForValue().get("num");
        if (StringUtils.isBlank(numString)){
            return ;
        }
        int num = Integer.parseInt(numString);
        this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

        // 解锁
//        lock.unlock();
    }

    public void testLock1() {

        String uuid = UUID.randomUUID().toString();
        // 争抢获取锁，所有请求执行setnx命令，只会有一个可以执行成功
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);

        // 判断，是否获取到锁，如果获取到锁，执行业务逻辑
        if (lock) {

            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)){
                return ;
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

            // 业务逻辑执行完成之后，别忘了释放锁
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script), Arrays.asList("lock"), uuid);
//            if (StringUtils.equals(uuid, this.redisTemplate.opsForValue().get("lock"))){
//
//                this.redisTemplate.delete("lock");
//            }
        } else {
            // 如果没有获取到锁，进行重试
            try {
                TimeUnit.SECONDS.sleep(1);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public String testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.readLock().lock(10, TimeUnit.SECONDS);

        String msg = this.redisTemplate.opsForValue().get("msg");
        return msg;
    }

    public void testWrite() {

        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.writeLock().lock(10, TimeUnit.SECONDS);

        this.redisTemplate.opsForValue().set("msg", UUID.randomUUID().toString());
    }

    public String testLatch() {
        try {
            RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");
            latch.trySetCount(6);
            latch.await();

            return "班长锁门。。。。。。";
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String testCountDown() {
        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");

        return "出来了一位同学。。。。";
    }


}
