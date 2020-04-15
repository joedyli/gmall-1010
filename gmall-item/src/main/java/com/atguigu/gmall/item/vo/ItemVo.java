package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.vo.AttrValueVo;
import com.atguigu.gmall.pms.vo.ItemCategoryVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ItemVo {

    // 三级分类 O Y
    private List<ItemCategoryVo> categoreis;

    // 品牌 V Y
    private Long brandId;
    private String brandName;

    // spu V Y
    private Long spuId;
    private String spuName;

    // sku V Y
    private Long skuId;
    private String title;
    private String subTitle;
    private BigDecimal price;
    private Integer weight;
    private String defaltImage;

    // sku图片 V Y
    private List<SkuImagesEntity> images;

    // 营销信息 O Y
    private List<ItemSaleVo> sales;

    // 是否有货 V Y
    private Boolean store = false;

    // sku所属spu下的所有sku的销售属性 O Y
    private List<AttrValueVo> saleAttrs;

    // spu的海报信息 V
    private List<String> spuImages;

    // 规格参数组及组下的规格参数(带值) O
    private List<ItemGroupVo> groups;
}
