package com.atguigu.gmall.order.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("confirm")
    public ResponseVo<OrderConfirmVo> confirm(){

        OrderConfirmVo confirmVo = this.orderService.confirm();
        return ResponseVo.ok(confirmVo);
    }

    @PostMapping("submit")
    public ResponseVo<Object> submit(@RequestBody OrderSubmitVo submitVo){

        this.orderService.submit(submitVo);

        return ResponseVo.ok();
    }

}
