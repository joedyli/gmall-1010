package com.atguigu.gmall.item.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.item.vo.ItemVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
//@RequestMapping("item")
public class ItemController {

    @Autowired
    private ItemService itemService;

    @GetMapping("{skuId}")
    public String load(@PathVariable("skuId")Long skuId, Model model){

        ItemVo itemVo = this.itemService.load(skuId);
        model.addAttribute("itemVo", itemVo);

        return "item";
    }

//    @GetMapping("{skuId}")
//    public ResponseVo<ItemVo> load(@PathVariable("skuId")Long skuId){
//
//        ItemVo itemVo = this.itemService.load(skuId);
//
//        return ResponseVo.ok(itemVo);
//    }
}
