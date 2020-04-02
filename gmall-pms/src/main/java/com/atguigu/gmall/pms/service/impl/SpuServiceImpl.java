package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.*;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SaleVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.Time;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Autowired
    private SpuDescMapper descMapper;

    @Autowired
    private SpuAttrValueService spuAttrValueService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuImagesService skuImagesService;

    @Autowired
    private SkuAttrValueService skuAttrValueService;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private SpuDescService descService;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpusByCidPage(PageParamVo pageParamVo, Long categoryId) {

        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();

        // 判断分类id是否为0，为0
        if (categoryId != 0) {
            wrapper.eq("category_id", categoryId);
        }

        String key = pageParamVo.getKey();
        if (StringUtils.isNotBlank(key)){
            wrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(
                pageParamVo.getPage(),
                wrapper
        );

        return new PageResultVo(page);
    }

    @Override
    @Transactional(readOnly = true)
    public void bigSave(SpuVo spuVo) throws FileNotFoundException {

        // 1.保存spu相关信息
        // 1.1. 保存spu的信息 pms_spu
        this.saveSpu(spuVo);

        // 1.2. 保存spu的描述信息 pms_spu_desc
//        this.saveSpuDesc(spuVo);
        this.descService.saveSpuDesc(spuVo);

//        try {
//            TimeUnit.SECONDS.sleep(4);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

//        int i = 1/0;
//        new FileInputStream("xxxx");

        // 1.3. 保存spu的基本属性及值 pms_spu_attr_value
        this.saveBaseAttr(spuVo);

        // 2.保存sku相关信息
        this.saveSku(spuVo);
    }

    private void saveSku(SpuVo spuVo) {
        List<SkuVo> skus = spuVo.getSkus();
        if (CollectionUtils.isEmpty(skus)){
            return ;
        }

        skus.forEach(skuVo -> {
            // 2.1. 保存sku的信息 pms_sku
            skuVo.setSpuId(spuVo.getId());
            skuVo.setBrandId(spuVo.getBrandId());
            skuVo.setCategoryId(spuVo.getCategoryId());
            List<String> images = skuVo.getImages();
            if (!CollectionUtils.isEmpty(images)){
                skuVo.setDefaultImage(StringUtils.isNotBlank(skuVo.getDefaultImage()) ? skuVo.getDefaultImage() : images.get(0));
            }
            this.skuMapper.insert(skuVo);

            // 2.2. 保存sku的图片信息 pms_sku_images
            if (!CollectionUtils.isEmpty(images)){
                List<SkuImagesEntity> skuImagesEntities = images.stream().map(url -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setUrl(url);
                    skuImagesEntity.setSkuId(skuVo.getId());
                    skuImagesEntity.setSort(0);
                    skuImagesEntity.setDefaultStatus(StringUtils.equals(url, skuVo.getDefaultImage()) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList());
                this.skuImagesService.saveBatch(skuImagesEntities);
            }

            // 2.3. 保存sku的销售属性 pms_sku_attr_value
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                saleAttrs.forEach(saleAttr -> saleAttr.setSkuId(skuVo.getId()));
                this.skuAttrValueService.saveBatch(saleAttrs);
            }

            // 3.保存sku的营销信息
            SaleVo saleVo = new SaleVo();
            BeanUtils.copyProperties(skuVo, saleVo);
            saleVo.setSkuId(skuVo.getId());
            this.smsClient.saveSales(saleVo);
        });
    }

    private void saveBaseAttr(SpuVo spuVo) {
        List<SpuAttrValueVo> baseAttrs = spuVo.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            List<SpuAttrValueEntity> spuAttrValueEntityList = baseAttrs.stream().map(spuAttrValueVo -> {
                SpuAttrValueEntity attrValueEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(spuAttrValueVo, attrValueEntity);
                attrValueEntity.setSpuId(spuVo.getId());
                attrValueEntity.setSort(0);
                return attrValueEntity;
            }).collect(Collectors.toList());
            spuAttrValueService.saveBatch(spuAttrValueEntityList);
        }
    }


    private void saveSpu(SpuVo spuVo) {
        spuVo.setCreateTime(new Date());
        spuVo.setUpdateTime(spuVo.getCreateTime());
        this.save(spuVo);
    }

}
