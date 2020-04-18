package com.atguigu.gmall.cart.bean;

import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class Cart {

    private Long skuId;
    private String title;
    private String defaultImage;
    private List<SkuAttrValueEntity> saleAttrs; // 销售属性
    private BigDecimal price;
    private Integer count;
    private Boolean store; // 库存
    private List<ItemSaleVo> sales; // 营销信息
    private Boolean check; // 选中状态
}
