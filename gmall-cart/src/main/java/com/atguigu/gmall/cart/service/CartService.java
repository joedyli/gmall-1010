package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.bean.Cart;
import com.atguigu.gmall.cart.bean.UserInfo;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.concurrent.ListenableFuture;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService cartAsyncService;

    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";

    public void addCart(Cart cart) {

        // 1.获取登录信息
        String userId = getUserId();
        String key = KEY_PREFIX + userId;

        // 2.获取redis中该用户的购物车
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 3.判断该用户的购物车信息是否已包含了该商品
        String skuId = cart.getSkuId().toString();
        BigDecimal count = cart.getCount(); // 用户添加购物的商品数量
        if (hashOps.hasKey(skuId)) {
            // 4.包含，更新数量
            String cartJson = hashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));
            this.cartAsyncService.updateCartByUserIdAndSkuId(cart);
        } else {
            // 5.不包含，给该用户新增购物车记录 skuId count
            cart.setUserId(userId);
            // 根据skuId查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                cart.setTitle(skuEntity.getTitle());
                cart.setPrice(skuEntity.getPrice());
                cart.setImage(skuEntity.getDefaultImage());
            }

            // 根据skuId查询销售属性
            ResponseVo<List<SkuAttrValueEntity>> skuattrValueResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = skuattrValueResponseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));

            // 根据skuId查询营销信息
            ResponseVo<List<ItemSaleVo>> itemSaleVoResposneVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = itemSaleVoResposneVo.getData();
            cart.setSales(JSON.toJSONString(itemSaleVos));

            // 根据skuId查询库存信息
            ResponseVo<List<WareSkuEntity>> listResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = listResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
            // 商品刚加入购物车时，默认为选中状态
            cart.setCheck(true);
            this.cartAsyncService.saveCart(cart);

            // 缓存实时价格
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuEntity.getPrice().toString());
        }
        hashOps.put(skuId, JSON.toJSONString(cart));
    }

    public Cart queryCartBySkuId(Long skuId) {
        // 1.获取登录信息
        String userId = getUserId();
        String key = KEY_PREFIX + userId;

        // 2.获取redis中该用户的购物车
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        if (hashOps.hasKey(skuId.toString())){
            String cartJson = hashOps.get(skuId.toString()).toString();
            return JSON.parseObject(cartJson, Cart.class);
        }
        throw new RuntimeException("您的购物车中没有该商品记录！");
    }

    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if (userInfo.getUserId() != null) {
            // 如果用户的id不为空，说明该用户已登录，添加购物车应该以userId作为key
            return userInfo.getUserId().toString();
        }
        // 否则，说明用户未登录，以userKey作为key
        return userInfo.getUserKey();
    }

    public List<Cart> queryCarts() {

        // 查询未登录的购物车
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String unloginKey = KEY_PREFIX + userInfo.getUserKey(); // 未登录情况下的外层map的key
        BoundHashOperations<String, Object, Object> unLoginHashOps = this.redisTemplate.boundHashOps(unloginKey);
        // 获取内层map的所有value（cart的json字符串）
        List<Object> unloginCartJsons = unLoginHashOps.values();
        List<Cart> unloginCarts = null;
        if (!CollectionUtils.isEmpty(unloginCartJsons)) {
            // 反序列化为List<Cart>集合
            unloginCarts = unloginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                // 查询实时价格
                String currentPriceString = this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
                cart.setCurrentPrice(new BigDecimal(currentPriceString));
                return cart;
            }).collect(Collectors.toList());
        }

        // 获取登陆状态，未登录直接返回
        if (userInfo.getUserId() == null) {
            return unloginCarts;
        }

        // 合并到登录状态的购物车
        String loginKey = KEY_PREFIX + userInfo.getUserId();
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(loginKey);
        if (!CollectionUtils.isEmpty(unloginCarts)) {
            unloginCarts.forEach(cart -> {
                String skuId = cart.getSkuId().toString();
                if (loginHashOps.hasKey(skuId)) {
                    // 登录状态的购物车包含了这条购物车记录，合并数量
                    String cartJson = loginHashOps.get(skuId).toString();
                    BigDecimal count = cart.getCount();
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));
                    // 更新mysql
                    this.cartAsyncService.updateCartByUserIdAndSkuId(cart);
                } else {
                    // 新增mysql
                    cart.setUserId(userInfo.getUserId().toString());
                    this.cartAsyncService.saveCart(cart);
                }
                // 更新redis
                loginHashOps.put(skuId, JSON.toJSONString(cart));
            });
            // 删除未登录的购物车，删除redis及mysql中未登录用户的购物车
            this.cartAsyncService.deleteCartsByUserId(userInfo.getUserKey());
            this.redisTemplate.delete(unloginKey);
        }

        // 查询登录状态的购物并返回
        List<Object> loginCartJsons = loginHashOps.values();
        if (CollectionUtils.isEmpty(loginCartJsons)) {
            return null;
        }
        return loginCartJsons.stream().map(cartJson -> {
            Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
            // 设置实时价格
            String currentPriceString = this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
            cart.setCurrentPrice(new BigDecimal(currentPriceString));
            return cart;
        }).collect(Collectors.toList());
    }

    public void updateNum(Cart cart) {

        // 获取外层map的key
        String userId = this.getUserId();
        String key = KEY_PREFIX + userId;

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 判断该用户的购物车中是否包含该商品
        if (hashOps.hasKey(cart.getSkuId().toString())) {
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            BigDecimal count = cart.getCount();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(count);
            this.cartAsyncService.updateCartByUserIdAndSkuId(cart);
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }

    public void check(Cart cart) {

        // 获取外层map的key
        String userId = this.getUserId();
        String key = KEY_PREFIX + userId;

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 判断该用户的购物车中是否包含该商品
        if (hashOps.hasKey(cart.getSkuId().toString())) {
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            Boolean check = cart.getCheck();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCheck(check);
            this.cartAsyncService.updateCartByUserIdAndSkuId(cart);
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }

    public void deleteCart(Long skuId) {
        // 获取外层map的key
        String userId = this.getUserId();
        String key = KEY_PREFIX + userId;

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        if (hashOps.hasKey(skuId.toString())) {
            this.cartAsyncService.deleteByUserIdAndSkuId(userId, skuId);
            hashOps.delete(skuId.toString());
        }
    }

    public List<Cart> queryCheckedCarts(Long userId) {

        // 外层map的key
        String key = KEY_PREFIX + userId;
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 获取该用户的所有购物车记录
        List<Object> cartJsons = hashOps.values();

        if (CollectionUtils.isEmpty(cartJsons)) {
            return null;
        }

        // 反序列化购物车，过滤出选中状态的购物车
        return cartJsons.stream().map(cartJson -> JSON.parseObject(cartJson.toString(), Cart.class)).filter(Cart::getCheck).collect(Collectors.toList());
    }

    @Async
    public ListenableFuture<String> executor1() {
        try {
            System.out.println("executor1方法开始执行");
            TimeUnit.SECONDS.sleep(4);
            System.out.println("executor1方法结束执行。。。");
            return AsyncResult.forValue("executor1"); // 正常响应
        } catch (InterruptedException e) {
            e.printStackTrace();
            return AsyncResult.forExecutionException(e); // 异常响应
        }
    }

    @Async
    public String executor2() {
        try {
            System.out.println("executor2方法开始执行");
            TimeUnit.SECONDS.sleep(5);
            System.out.println("executor2方法结束执行。。。");
            int i = 1 / 0; // 制造异常
            return "executor2"; // 正常响应
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
