package com.atguigu.gmall.order.service;

import com.atguigu.gmall.cart.bean.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.order.vo.OrderItemVo;
import com.atguigu.gmall.order.vo.UserInfo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "order:token:";

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public OrderConfirmVo confirm() {
        OrderConfirmVo confirmVo = new OrderConfirmVo();

        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        // 查询送货清单
        CompletableFuture<List<Cart>> cartCompletableFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<List<Cart>> listResponseVo = this.cartClient.queryCheckedCarts(userId);
            List<Cart> carts = listResponseVo.getData();
            if (CollectionUtils.isEmpty(carts)) {
                throw new OrderException("没有选中的购物车信息!");
            }
            return carts;
        }, threadPoolExecutor);
        CompletableFuture<Void> itemCompletableFuture = cartCompletableFuture.thenAcceptAsync(carts -> {
            List<OrderItemVo> items = carts.stream().map(cart -> {
                OrderItemVo orderItemVo = new OrderItemVo();
                orderItemVo.setSkuId(cart.getSkuId());
                orderItemVo.setCount(cart.getCount());
                // 根据skuId查询sku
                CompletableFuture<Void> skuCompletableFuture = CompletableFuture.runAsync(() -> {
                    ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
                    SkuEntity skuEntity = skuEntityResponseVo.getData();
                    orderItemVo.setTitle(skuEntity.getTitle());
                    orderItemVo.setPrice(skuEntity.getPrice());
                    orderItemVo.setDefaultImage(skuEntity.getDefaultImage());
                    orderItemVo.setWeight(new BigDecimal(skuEntity.getWeight()));
                }, threadPoolExecutor);
                // 查询销售属性
                CompletableFuture<Void> saleAttrCompletableFuture = CompletableFuture.runAsync(() -> {
                    ResponseVo<List<SkuAttrValueEntity>> skuAttrValueResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(cart.getSkuId());
                    List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValueResponseVo.getData();
                    orderItemVo.setSaleAttrs(skuAttrValueEntities);
                }, threadPoolExecutor);

                // 根据skuId查询营销信息
                CompletableFuture<Void> saleCompletableFuture = CompletableFuture.runAsync(() -> {
                    ResponseVo<List<ItemSaleVo>> itemSaleVoResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
                    List<ItemSaleVo> itemSaleVos = itemSaleVoResponseVo.getData();
                    orderItemVo.setSales(itemSaleVos);
                }, threadPoolExecutor);

                // 根据 skuId查询库存信息
                CompletableFuture<Void> storeCompletableFuture = CompletableFuture.runAsync(() -> {
                    ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
                    List<WareSkuEntity> wareSkuEntities = wareSkuResponseVo.getData();
                    if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                        orderItemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                    }
                }, threadPoolExecutor);
                CompletableFuture.allOf(skuCompletableFuture, saleAttrCompletableFuture, saleCompletableFuture, storeCompletableFuture).join();
                return orderItemVo;
            }).collect(Collectors.toList());
            confirmVo.setItems(items);
        }, threadPoolExecutor);

        // 查询收货地址列表
        CompletableFuture<Void> addressCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<UserAddressEntity>> addressesResponseVo = this.umsClient.queryAddressesByUserId(userId);
            List<UserAddressEntity> addresses = addressesResponseVo.getData();
            confirmVo.setAddresses(addresses);
        }, threadPoolExecutor);

        // 查询用户的积分信息
        CompletableFuture<Void> boundsCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
            UserEntity userEntity = userEntityResponseVo.getData();
            if (userEntity != null) {
                confirmVo.setBounds(userEntity.getIntegration());
            }
        }, threadPoolExecutor);

        // 防重的唯一标识
        CompletableFuture<Void> tokenCompletableFuture = CompletableFuture.runAsync(() -> {
            String timeId = IdWorker.getTimeId();
            this.redisTemplate.opsForValue().set(KEY_PREFIX + timeId, timeId);
            confirmVo.setOrderToken(timeId);
        }, threadPoolExecutor);

        CompletableFuture.allOf(itemCompletableFuture, addressCompletableFuture, boundsCompletableFuture, tokenCompletableFuture).join();

        return confirmVo;
    }
}
