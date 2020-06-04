package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.cart.bean.Cart;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CartAsyncService {

    @Autowired
    private CartMapper cartMapper;

    @Async
    public void updateCartByUserIdAndSkuId(Cart cart){
        this.cartMapper.update(cart, new UpdateWrapper<Cart>().eq("user_id", cart.getUserId()).eq("sku_id", cart.getSkuId()));
    }

    @Async
    public void saveCart(Cart cart){
        this.cartMapper.insert(cart);
    }

    @Async
    public void deleteCartsByUserId(String userKey){
        this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id", userKey));
    }

    @Async
    public void deleteByUserIdAndSkuId(String userKey, Long skuId){
        this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id", userKey).eq("sku_id", skuId));
    }
}
