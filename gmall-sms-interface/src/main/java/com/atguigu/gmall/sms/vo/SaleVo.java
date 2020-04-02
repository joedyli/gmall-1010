package com.atguigu.gmall.sms.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SaleVo {

    private Long skuId;

    // sku积分相关信息
    private BigDecimal growBounds;
    private BigDecimal buyBounds;
    private List<Integer> work;

    // sku满减信息
    private BigDecimal fullPrice;
    private BigDecimal reducePrice;
    private Integer fullAddOther;

    // sku打折信息
    private Integer fullCount;
    private BigDecimal discount;
    private Integer ladderAddOther;
}
