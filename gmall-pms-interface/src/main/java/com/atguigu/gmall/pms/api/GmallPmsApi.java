package com.atguigu.gmall.pms.api;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemCategoryVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface GmallPmsApi {

    @GetMapping("pms/attrgroup/attr/withvalue")
    public ResponseVo<List<ItemGroupVo>> queryGoupsWithAttrValues(
            @RequestParam("cid") Long cid,
            @RequestParam("spuId") Long spuId,
            @RequestParam("skuId") Long skuId
    );

    @GetMapping("pms/skuattrvalue/spu/{spuId}")
    public ResponseVo<List<SkuAttrValueEntity>> querySkuAttrValuesBySpuId(@PathVariable("spuId")Long spuId);

    @GetMapping("pms/category/all/{cid3}")
    public ResponseVo<List<ItemCategoryVo>> queryCategoriesByCid3(@PathVariable("cid3")Long cid3);

    @GetMapping("pms/spudesc/{spuId}")
    public ResponseVo<SpuDescEntity> querySpuDescById(@PathVariable("spuId") Long spuId);

    @GetMapping("pms/sku/{id}")
    public ResponseVo<SkuEntity> querySkuById(@PathVariable("id") Long id);

    @GetMapping("pms/skuimages/sku/{skuId}")
    public ResponseVo<List<SkuImagesEntity>> queryImagesBySkuId(@PathVariable("skuId")Long skuId);

    @GetMapping("pms/skuattrvalue/sku/{skuId}")
    public ResponseVo<List<SkuAttrValueEntity>> querySkuAttrValuesBySkuId(@PathVariable("skuId")Long skuId);

    @PostMapping("pms/spu/page")
    public ResponseVo<List<SpuEntity>> querySpuByPage(@RequestBody PageParamVo paramVo);

    @GetMapping("pms/sku/spu/{spuId}")
    public ResponseVo<List<SkuEntity>> querySkusBySpuId(@PathVariable("spuId")Long spuId);

    @GetMapping("pms/brand/{id}")
    public ResponseVo<BrandEntity> queryBrandById(@PathVariable("id") Long id);

    @GetMapping("pms/category/{id}")
    public ResponseVo<CategoryEntity> queryCategoryById(@PathVariable("id") Long id);

    @GetMapping("pms/attr/category/{cid}")
    public ResponseVo<List<AttrEntity>> queryAttrsByCid(@PathVariable("cid")Long cid,
                                                        @RequestParam(value = "type", required = false)Integer type,
                                                        @RequestParam(value = "searchType", required = false)Integer searchType);

    @GetMapping("pms/skuattrvalue/search/attr")
    public ResponseVo<List<SkuAttrValueEntity>> querySkuSearchAttrValue(
            @RequestParam("skuId")Long skuId,
            @RequestParam("attrIds")List<Long> attrIds
    );

    @GetMapping("pms/spuattrvalue/search/attr")
    public ResponseVo<List<SpuAttrValueEntity>> querySpuSearchAttrValue(
            @RequestParam("spuId")Long spuId,
            @RequestParam("attrIds")List<Long> attrIds
    );

    @GetMapping("pms/spu/{id}")
    @ApiOperation("详情查询")
    public ResponseVo<SpuEntity> querySpuById(@PathVariable("id") Long id);

    @GetMapping("pms/category/parent/{parentId}")
    public ResponseVo<List<CategoryEntity>> queryCategoriesByPid(@PathVariable("parentId")Long pid);

    @GetMapping("pms/category/subs/{pid}")
    public ResponseVo<List<CategoryEntity>> queryCategoriesWithSub(@PathVariable("pid")Long pid);
}
