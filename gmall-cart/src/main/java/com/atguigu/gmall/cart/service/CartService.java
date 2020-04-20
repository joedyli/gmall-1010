package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.bean.Cart;
import com.atguigu.gmall.cart.bean.UserInfo;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import springfox.documentation.spring.web.json.Json;

import javax.swing.plaf.basic.BasicScrollPaneUI;
import java.util.List;
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

    private static final String KEY_PREFIX = "cart:";

    public void addCart(Cart cart) {

        // 1.获取登录信息
        String key = generatedKey();

        // 2.获取redis中该用户的购物车
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 3.判断该用户的购物车信息是否已包含了该商品
        String skuId = cart.getSkuId().toString();
        Integer count = cart.getCount(); // 用户添加购物的商品数量
        if (hashOps.hasKey(skuId)) {
            // 4.包含，更新数量
            String cartJson = hashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount() + count);
        } else {
            // 5.不包含，给该用户新增购物车记录 skuId count
            // 根据skuId查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            cart.setTitle(skuEntity.getTitle());
            cart.setPrice(skuEntity.getPrice());
            cart.setDefaultImage(skuEntity.getDefaultImage());

            // 根据skuId查询销售属性
            ResponseVo<List<SkuAttrValueEntity>> skuattrValueResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = skuattrValueResponseVo.getData();
            cart.setSaleAttrs(skuAttrValueEntities);

            // 根据skuId查询营销信息
            ResponseVo<List<ItemSaleVo>> itemSaleVoResposneVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = itemSaleVoResposneVo.getData();
            cart.setSales(itemSaleVos);

            // 根据skuId查询库存信息
            ResponseVo<List<WareSkuEntity>> listResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = listResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
            // 商品刚加入购物车时，默认为选中状态
            cart.setCheck(true);
//            hashOps.put(skuId, JSON.toJSONString(cart));
        }
        hashOps.put(skuId, JSON.toJSONString(cart));
    }

    private String generatedKey() {
        String key = KEY_PREFIX;
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if (userInfo.getUserId() != null) {
            // 如果用户的id不为空，说明该用户已登录，添加购物车应该以userId作为key
            key += userInfo.getUserId();
        } else {
            // 否则，说明用户未登录，以userKey作为key
            key += userInfo.getUserKey();
        }
        return key;
    }

    public List<Cart> queryCarts() {

        // 查询未登录的购物车
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String unloginKey = KEY_PREFIX + userInfo.getUserKey(); // 未登录情况下的外层map的key
        BoundHashOperations<String, Object, Object> unLoginHashOps = this.redisTemplate.boundHashOps(unloginKey);
        // 获取内层map的所有value（cart的json字符串）
        List<Object> unloginCartJsons = unLoginHashOps.values();
        List<Cart> unloginCarts = null;
        if (!CollectionUtils.isEmpty(unloginCartJsons)){
            // 反序列化为List<Cart>集合
            unloginCarts = unloginCartJsons.stream().map(cartJson -> JSON.parseObject(cartJson.toString(), Cart.class)).collect(Collectors.toList());
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
                    Integer count = cart.getCount();
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount() + count);
                }
                loginHashOps.put(skuId, JSON.toJSONString(cart));
            });
        }

        // 删除未登录的购物车
        this.redisTemplate.delete(unloginKey);

        // 查询登录状态的购物并返回
        List<Object> loginCartJsons = loginHashOps.values();
        if (CollectionUtils.isEmpty(loginCartJsons)) {
            return null;
        }
        return loginCartJsons.stream().map(cartJson -> JSON.parseObject(cartJson.toString(), Cart.class)).collect(Collectors.toList());
    }

    public void updateNum(Cart cart) {

        // 获取外层map的key
        String key = this.generatedKey();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 判断该用户的购物车中是否包含该商品
        if (hashOps.hasKey(cart.getSkuId().toString())){
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            Integer count = cart.getCount();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(count);
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }

    public void check(Cart cart) {

        // 获取外层map的key
        String key = this.generatedKey();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 判断该用户的购物车中是否包含该商品
        if (hashOps.hasKey(cart.getSkuId().toString())){
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            Boolean check = cart.getCheck();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCheck(check);
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }

    public void deleteCart(Long skuId) {
        // 获取外层map的key
        String key = this.generatedKey();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        if (hashOps.hasKey(skuId.toString())) {
            hashOps.delete(skuId.toString());
        }
    }
}
