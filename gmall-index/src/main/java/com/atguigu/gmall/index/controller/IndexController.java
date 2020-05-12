package com.atguigu.gmall.index.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class IndexController {

    @Autowired
    private IndexService indexService;

    @GetMapping
    public String toIndex(Model model){

        List<CategoryEntity> categoryEntities = this.indexService.queryLvl1Categories();
        model.addAttribute("cates", categoryEntities);

        // TODO: 加载其他数据

        return "index";
    }

    @ResponseBody
    @GetMapping("index/cates/{pid}")
    public ResponseVo<List<CategoryEntity>> queryLvl2CategoriesWithSub(@PathVariable("pid")Long pid){

        List<CategoryEntity> categoryEntities = this.indexService.queryLvl2CategoriesWithSub(pid);
        return ResponseVo.ok(categoryEntities);
    }

    @GetMapping("test/lock")
    public ResponseVo<Object> testLock(){
        this.indexService.testLock();
        return ResponseVo.ok();
    }

    @GetMapping("test/read")
    public ResponseVo<Object> testRead(){
        String msg = this.indexService.testRead();
        return ResponseVo.ok(msg);
    }

    @GetMapping("test/write")
    public ResponseVo<Object> testWrite(){
        this.indexService.testWrite();
        return ResponseVo.ok();
    }

    @GetMapping("test/latch")
    public ResponseVo<Object> testLatch(){
        String msg = this.indexService.testLatch();
        return ResponseVo.ok(msg);
    }

    @GetMapping("test/down")
    public ResponseVo<Object> testCountDown(){
        String msg = this.indexService.testCountDown();
        return ResponseVo.ok(msg);
    }
}
