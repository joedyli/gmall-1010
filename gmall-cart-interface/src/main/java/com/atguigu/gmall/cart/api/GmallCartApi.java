package com.atguigu.gmall.cart.api;

import com.atguigu.gmall.cart.bean.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

public interface GmallCartApi {

    @GetMapping("check/{userId}")
    public ResponseVo<List<Cart>> queryCheckedCarts(@PathVariable("userId")Long userId);
}
