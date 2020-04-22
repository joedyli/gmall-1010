package com.atguigu.gmall.oms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.feign.GmallPmsClient;
import com.atguigu.gmall.oms.feign.GmallSmsClient;
import com.atguigu.gmall.oms.feign.GmallUmsClient;
import com.atguigu.gmall.oms.service.OrderItemService;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<OrderEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<OrderEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public OrderEntity saveOrder(OrderSubmitVo submitVo) {
        // 1.保存订单表
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setUserId(submitVo.getUserId());
        orderEntity.setOrderSn(submitVo.getOrderToken());
        orderEntity.setCreateTime(new Date());

        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(submitVo.getUserId());
        UserEntity userEntity = userEntityResponseVo.getData();
        if (userEntity != null) {
            orderEntity.setUsername(userEntity.getUsername());
        }

        BigDecimal integrationAmount = new BigDecimal(submitVo.getBounds() / 100);
        BigDecimal freightAmount = new BigDecimal(10);
        orderEntity.setTotalAmount(submitVo.getTotalPrice());
        orderEntity.setPayAmount(submitVo.getTotalPrice().subtract(integrationAmount).add(freightAmount));
        orderEntity.setFreightAmount(freightAmount);
        orderEntity.setIntegrationAmount(integrationAmount);

        orderEntity.setPayType(submitVo.getPayType());
        orderEntity.setSourceType(submitVo.getSouceType());
        orderEntity.setStatus(0);
        orderEntity.setDeliveryCompany(submitVo.getDeliveryCompany());
        orderEntity.setAutoConfirmDay(14);

        // TODO: 订单所有商品赠送的成长积分和购物积分
        orderEntity.setIntegration(null);
        orderEntity.setGrowth(null);

        // TODO：发票相关

        UserAddressEntity address = submitVo.getAddress();
        if (address != null) {
            orderEntity.setReceiverCity(address.getCity());
            orderEntity.setReceiverName(address.getName());
            orderEntity.setReceiverPhone(address.getPhone());
            orderEntity.setReceiverPostCode(address.getPostCode());
            orderEntity.setReceiverProvince(address.getProvince());
            orderEntity.setReceiverRegion(address.getRegion());
            orderEntity.setReceiverAddress(address.getAddress());
        }

        orderEntity.setConfirmStatus(0);
        orderEntity.setDeleteStatus(1);
        orderEntity.setUseIntegration(submitVo.getBounds());

        this.save(orderEntity);

        // 2.保存订单详情表
        List<OrderItemVo> items = submitVo.getItems();
        if (!CollectionUtils.isEmpty(items)) {
            List<OrderItemEntity> itemEntities = items.stream().map(itemVo -> {
                OrderItemEntity itemEntity = new OrderItemEntity();
                itemEntity.setOrderId(orderEntity.getId());
                itemEntity.setOrderSn(submitVo.getOrderToken());

                // 根据skuid查询sku
                ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(itemVo.getSkuId());
                SkuEntity skuEntity = skuEntityResponseVo.getData();
                if (skuEntity != null){
                    itemEntity.setSkuId(skuEntity.getId());
                    itemEntity.setSkuName(skuEntity.getName());
                    itemEntity.setSkuPic(skuEntity.getDefaultImage());
                    itemEntity.setSkuPrice(skuEntity.getPrice());
                    itemEntity.setSkuQuantity(itemVo.getCount());
                    itemEntity.setCategoryId(skuEntity.getCategoryId());

                    ResponseVo<List<SkuAttrValueEntity>> listResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(itemVo.getSkuId());
                    List<SkuAttrValueEntity> skuAttrValueEntities = listResponseVo.getData();
                    itemEntity.setSkuAttrsVals(JSON.toJSONString(skuAttrValueEntities));

                    ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
                    SpuEntity spuEntity = spuEntityResponseVo.getData();
                    if (spuEntity != null) {
                        itemEntity.setSpuId(spuEntity.getId());
                        itemEntity.setSpuName(spuEntity.getName());
                    }

                    ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
                    SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
                    if (spuDescEntity != null){
                        itemEntity.setSpuPic(spuDescEntity.getDecript());
                    }

                    ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
                    BrandEntity brandEntity = brandEntityResponseVo.getData();
                    if (brandEntity != null) {
                        itemEntity.setSpuBrand(brandEntity.getName());
                    }

                    ResponseVo<SkuBoundsEntity> skuBoundsEntityResponseVo = this.smsClient.queryBoundsBySkuId(itemVo.getSkuId());
                    SkuBoundsEntity skuBoundsEntity = skuBoundsEntityResponseVo.getData();
                    if (skuBoundsEntity != null) {
                        itemEntity.setGiftIntegration(skuBoundsEntity.getBuyBounds().intValue());
                        itemEntity.setGiftGrowth(skuBoundsEntity.getGrowBounds().intValue());
                    }
                }
                return itemEntity;
            }).collect(Collectors.toList());
            this.orderItemService.saveBatch(itemEntities);
        }

        this.rabbitTemplate.convertAndSend("order-exchange", "order.ttl", submitVo.getOrderToken());

        return orderEntity;
    }

}
