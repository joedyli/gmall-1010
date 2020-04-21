package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId;
    private Integer count;
    private Boolean lock; // 该商品锁定状态
    private Long wareSkuId; // 锁定的库存id
    private String orderToken; // 订单的唯一标识
}
