package com.atguigu.gmall.sms.service.impl;

import org.springframework.stereotype.Service;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.sms.mapper.SpuBoundsMapper;
import com.atguigu.gmall.sms.entity.SpuBoundsEntity;
import com.atguigu.gmall.sms.service.SpuBoundsService;


@Service("spuBoundsService")
public class SpuBoundsServiceImpl extends ServiceImpl<SpuBoundsMapper, SpuBoundsEntity> implements SpuBoundsService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuBoundsEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuBoundsEntity>()
        );

        return new PageResultVo(page);
    }

}