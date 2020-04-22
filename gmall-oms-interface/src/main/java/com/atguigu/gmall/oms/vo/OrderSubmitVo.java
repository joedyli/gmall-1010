package com.atguigu.gmall.oms.vo;

import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVo {

    private Long userId;

    private UserAddressEntity address; // 收获地址

    private Integer payType; // 支付方式

    private String deliveryCompany; // 物流公司/配送方式

    private List<OrderItemVo> items; // 送货清单

    // TODO： 发票信息

    // TODO：优惠券

    private Integer bounds; // 使用的积分

    private BigDecimal totalPrice; // 页面总价信息，验价需要的信息

    private String orderToken; // 防重

    private Integer souceType; // 数据来源
}
