package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.order.config.AlipayTemplate;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.order.vo.PayAsyncVo;
import com.atguigu.gmall.order.vo.PayVo;
import com.atguigu.gmall.order.vo.UserInfo;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @GetMapping("confirm")
    public String confirm(Model model){

        OrderConfirmVo confirmVo = this.orderService.confirm();
        model.addAttribute("confirmVo", confirmVo);
        return "trade";
    }

    @PostMapping("submit")
    @ResponseBody
    public ResponseVo<Object> submit(@RequestBody OrderSubmitVo submitVo){

        OrderEntity orderEntity = this.orderService.submit(submitVo);

//        try {
//            // 支付页面：调用ali的支付接口
//            PayVo payVo = new PayVo();
//            payVo.setOut_trade_no(orderEntity.getOrderSn());
//            payVo.setTotal_amount(orderEntity.getPayAmount().toString());
//            payVo.setSubject("谷粒商城支付平台");
//            payVo.setBody("谷粒商城订单支付");
//            String form = this.alipayTemplate.pay(payVo);
//
//            return ResponseVo.ok(form);
//        } catch (AlipayApiException e) {
//            e.printStackTrace();
//        }
        return ResponseVo.ok(orderEntity.getOrderSn());
    }

    @PostMapping("pay/success")
    public ResponseVo<Object> paySuccess(PayAsyncVo payAsyncVo){

        System.out.println("支付成功！！" + payAsyncVo.getOut_trade_no());
        // TODO
        this.rabbitTemplate.convertAndSend("order-exchange", "pay.success", payAsyncVo.getOut_trade_no());

        return ResponseVo.ok();
    }

    @PostMapping("seckill/{skuId}")
    public ResponseVo<Object> seckill(@PathVariable("skuId")Long skuId){

        UserInfo userInfo = LoginInterceptor.getUserInfo();

        String stockJson = this.redisTemplate.opsForValue().get("sec:kill:" + skuId);
        if (StringUtils.isBlank(stockJson)) {
            throw new OrderException("手慢了，秒杀结束。欢迎下次再来！");
        }

        Integer stock = Integer.parseInt(stockJson);

        RSemaphore semaphore = this.redissonClient.getSemaphore("lock:" + skuId);
        semaphore.trySetPermits(stock);

        String stockJson2 = this.redisTemplate.opsForValue().get("sec:kill:" + skuId);
        Integer stock2 = Integer.parseInt(stockJson2);

        // 直接从缓存中减库存
        if (stock2 > 0) {
            this.redisTemplate.opsForValue().set("sec:kill:" + skuId, String.valueOf(--stock));
        } else {
            throw new OrderException("秒杀结束，请下次再来！");
        }

        SkuLockVo skuLockVo = new SkuLockVo();
        skuLockVo.setSkuId(skuId);
        skuLockVo.setCount(1);
        String timeId = IdWorker.getTimeId();
        skuLockVo.setOrderToken(timeId);
        skuLockVo.setUserId(userInfo.getUserId());
        // 异步方式创建订单
        this.rabbitTemplate.convertAndSend("order-exchange", "sec:kill", JSON.toJSONString(skuLockVo));

        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("count:down:" + userInfo.getUserId());
        countDownLatch.trySetCount(1);

        return ResponseVo.ok("恭喜您，秒杀成功！");
    }

    @GetMapping("seckill")
    public ResponseVo<Object> queryOrder() throws InterruptedException {
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        // 使用countdownlatch 防止出现秒杀成功，但是查询不到订单的情况下
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("count:down:" + userInfo.getUserId());
        countDownLatch.await();

        // TOODO: 查询订单

        return ResponseVo.ok();
    }

}
