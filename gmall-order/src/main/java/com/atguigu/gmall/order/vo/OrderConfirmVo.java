package com.atguigu.gmall.order.vo;

import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVo {

    // 收货地址列表
    private List<UserAddressEntity> addresses;

    // 送货清单
    private List<OrderItemVo> items;

    // 用户的购物积分信息
    private Integer bounds;

    private String orderToken; // 防重的唯一标识
}
