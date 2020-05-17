package com.atguigu.gmall.item.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.item.vo.ItemVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
//@RestController
//@RequestMapping("item")
public class ItemController {

    @Autowired
    private ItemService itemService;

    @GetMapping("{skuId}.html")
    public String load(@PathVariable("skuId")Long skuId, Model model){

        ItemVo itemVo = this.itemService.load(skuId);
        model.addAttribute("itemVo", itemVo);

        return "item";
    }

    @ResponseBody
    @GetMapping("item/{skuId}")
    public ResponseVo<ItemVo> load(@PathVariable("skuId")Long skuId){

        ItemVo itemVo = this.itemService.load(skuId);

        return ResponseVo.ok(itemVo);
    }
}
