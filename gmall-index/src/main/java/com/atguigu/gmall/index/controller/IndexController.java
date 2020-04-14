package com.atguigu.gmall.index.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.vo.CategoryVo;
import net.sf.jsqlparser.expression.operators.relational.OldOracleJoinBinaryExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("index")
public class IndexController {

    @Autowired
    private IndexService indexService;

    @GetMapping("cates")
    public ResponseVo<List<CategoryEntity>> queryLvl1Categories(){

        List<CategoryEntity> categoryEntities = this.indexService.queryLvl1Categories();

        return ResponseVo.ok(categoryEntities);
    }

    @GetMapping("cates/{pid}")
    public ResponseVo<List<CategoryVo>> queryLvl2CategoriesWithSub(@PathVariable("pid")Long pid){

        List<CategoryVo> categoryVos = this.indexService.queryLvl2CategoriesWithSub(pid);
        return ResponseVo.ok(categoryVos);
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
