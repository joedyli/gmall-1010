package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.bean.Cart;
import com.atguigu.gmall.cart.bean.UserInfo;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.Response;
import java.util.List;

@RestController
@RequestMapping("cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @PostMapping
    public ResponseVo<Object> addCart(@RequestBody Cart cart){
        this.cartService.addCart(cart);

        return ResponseVo.ok();
    }

    @GetMapping
    public ResponseVo<List<Cart>> queryCarts(){

        List<Cart> carts = this.cartService.queryCarts();
        return ResponseVo.ok(carts);
    }

    @PostMapping("update")
    public ResponseVo<Object> updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);

        return ResponseVo.ok();
    }

    @PostMapping("check")
    public ResponseVo<Object> check(@RequestBody Cart cart){
        this.cartService.check(cart);

        return ResponseVo.ok();
    }

    @PostMapping("{skuId}")
    public ResponseVo<Object> deleteCart(@PathVariable("skuId")Long skuId){

        this.cartService.deleteCart(skuId);

        return ResponseVo.ok();
    }

    @GetMapping("test")
    public ResponseVo<Object> test(HttpServletRequest request){

        System.out.println(LoginInterceptor.getUserInfo());

        return ResponseVo.ok();
    }
}
