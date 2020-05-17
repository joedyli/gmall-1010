package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;

import java.util.List;

/**
 * sku销售属性&值
 *
 * @author fengge
 * @email fengge@atguigu.com
 * @date 2020-03-31 11:01:44
 */
public interface SkuAttrValueService extends IService<SkuAttrValueEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<SaleAttrValueVo> querySkuAttrValuesBySpuId(Long spuId);

    String querySkusJsonBySpuId(Long spuId);
}

