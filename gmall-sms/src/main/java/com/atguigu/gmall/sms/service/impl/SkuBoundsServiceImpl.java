package com.atguigu.gmall.sms.service.impl;

import com.atguigu.gmall.sms.entity.SkuFullReductionEntity;
import com.atguigu.gmall.sms.entity.SkuLadderEntity;
import com.atguigu.gmall.sms.mapper.SkuFullReductionMapper;
import com.atguigu.gmall.sms.mapper.SkuLadderMapper;
import com.atguigu.gmall.sms.vo.SaleVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.sms.mapper.SkuBoundsMapper;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.gmall.sms.service.SkuBoundsService;

import java.util.List;


@Service("skuBoundsService")
public class SkuBoundsServiceImpl extends ServiceImpl<SkuBoundsMapper, SkuBoundsEntity> implements SkuBoundsService {

    @Autowired
    private SkuFullReductionMapper reductionMapper;

    @Autowired
    private SkuLadderMapper ladderMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuBoundsEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuBoundsEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public void saveSales(SaleVo saleVo) {
        // 3.1. 保存积分信息 sms_sku_bounds
        SkuBoundsEntity skuBoundsEntity = new SkuBoundsEntity();
        skuBoundsEntity.setSkuId(saleVo.getSkuId());
        skuBoundsEntity.setBuyBounds(saleVo.getBuyBounds());
        skuBoundsEntity.setGrowBounds(saleVo.getGrowBounds());
        List<Integer> works = saleVo.getWork();
        skuBoundsEntity.setWork(works.get(3) * 8 + works.get(2) * 4 + works.get(1) * 2 + works.get(0) * 1);
        this.save(skuBoundsEntity);

        // 3.2. 保存满减信息 sms_sku_full_reduction
        SkuFullReductionEntity reductionEntity = new SkuFullReductionEntity();
        reductionEntity.setSkuId(saleVo.getSkuId());
        reductionEntity.setFullPrice(saleVo.getFullPrice());
        reductionEntity.setReducePrice(saleVo.getReducePrice());
        reductionEntity.setAddOther(saleVo.getFullAddOther());
        this.reductionMapper.insert(reductionEntity);

        // 3.3. 保存打折信息 sms_sku_ladder
        SkuLadderEntity ladderEntity = new SkuLadderEntity();
        ladderEntity.setSkuId(saleVo.getSkuId());
        ladderEntity.setFullCount(saleVo.getFullCount());
        ladderEntity.setDiscount(saleVo.getDiscount());
        ladderEntity.setAddOther(saleVo.getLadderAddOther());
        this.ladderMapper.insert(ladderEntity);
    }

}
