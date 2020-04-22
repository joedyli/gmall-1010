package com.atguigu.gmall.oms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.oms.entity.PaymentInfoEntity;

import java.util.Map;

/**
 * 支付信息表
 *
 * @author fengge
 * @email fengge@atguigu.com
 * @date 2020-04-22 09:41:01
 */
public interface PaymentInfoService extends IService<PaymentInfoEntity> {

    PageResultVo queryPage(PageParamVo paramVo);
}

