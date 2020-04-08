package com.atguigu.gmall.pms.api;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface GmallPmsApi {

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
}
