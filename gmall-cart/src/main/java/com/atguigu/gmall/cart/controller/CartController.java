package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cart")
public class CartController {

    @GetMapping("test")
    public ResponseVo<Object> test(){

        return ResponseVo.ok();
    }
}
