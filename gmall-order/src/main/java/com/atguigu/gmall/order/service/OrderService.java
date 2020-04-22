package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.bean.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.order.vo.UserInfo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.naming.ldap.HasControls;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
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

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

    public void submit(OrderSubmitVo submitVo) {
        // 1.防重
        String orderToken = submitVo.getOrderToken();
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) " +
                "else return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), orderToken);
        if (!flag){
            throw new OrderException("请勿重复提交！");
        }

        // 2.验价（总价）
        List<OrderItemVo> items = submitVo.getItems();
        if (CollectionUtils.isEmpty(items)) {
            throw new OrderException("请选择要购买的商品！");
        }
        // 页面传递过来的总价
        BigDecimal totalPrice = submitVo.getTotalPrice();
        // 要获取数据库的实时价格
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                return skuEntity.getPrice().multiply(new BigDecimal(item.getCount()));
            }
            return new BigDecimal(0);
        }).reduce((a, b) -> a.add(b)).get();
        // 比较页面价格和数据库实时价格
        if (totalPrice.compareTo(currentTotalPrice) != 0) {
            throw new OrderException("页面已过期，请刷新页面后重试");
        }

        // 3.验库存并锁定库存
        List<SkuLockVo> skuLockVos = items.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setCount(item.getCount());
            skuLockVo.setOrderToken(orderToken);
            return skuLockVo;
        }).collect(Collectors.toList());
        // 如果锁定失败会相应锁定的具体信息
        ResponseVo<List<SkuLockVo>> skuLockVoResponseVo = this.wmsClient.checkAndLock(skuLockVos);
        List<SkuLockVo> lockVos = skuLockVoResponseVo.getData();
        // 响应的数据不为空，说明锁定失败
        if (!CollectionUtils.isEmpty(lockVos)){
            throw new OrderException(JSON.toJSONString(lockVos));
        }

        // 4.下单（新增订单：订单表，订单详情表）
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        try {
            submitVo.setUserId(userInfo.getUserId());
            ResponseVo<OrderEntity> orderEntityResponseVo = this.omsClient.save(submitVo);
            OrderEntity orderEntity = orderEntityResponseVo.getData();
        } catch (Exception e) {
            // 如果验库存锁库存成功，但是下单失败，应该立马解锁库存
            this.rabbitTemplate.convertAndSend("order-exchange", "stock.unlock", orderToken);
            // 如果订单创建出现异常的提示信息
            throw new OrderException("服务器错误！请稍后再试！");
        }

        // 5.删除对应的购物车记录
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userInfo.getUserId());
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        map.put("skuIds", JSON.toJSONString(skuIds));
        this.rabbitTemplate.convertAndSend("order-exchange", "cart.delete", map);
    }

//    public static void main(String[] args) {
//        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
//        scheduledExecutorService.scheduleAtFixedRate(() -> {
//            // 每隔一个指定的时间执行一次。每隔10分钟遍历订单表，判断有哪些订单的创建时间距当前时间超过30分钟，更新订单状态并解锁库存
//        }, 5, 10, TimeUnit.SECONDS);
//    }

//    @Scheduled(fixedRate = 10)
//    public void task(){
//        System.out.println("定时任务：" + System.currentTimeMillis());
//    }
}
