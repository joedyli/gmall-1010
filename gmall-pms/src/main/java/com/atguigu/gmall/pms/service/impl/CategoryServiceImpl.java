package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.vo.CategoryVo;
import com.atguigu.gmall.pms.vo.ItemCategoryVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.CategoryMapper;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.service.CategoryService;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, CategoryEntity> implements CategoryService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<CategoryEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<CategoryEntity> queryCategoriesByPid(Long pid) {

        QueryWrapper<CategoryEntity> wrapper = new QueryWrapper<>();

        // 判断pid是否为-1，如果为-1不用拼接该条件
        if (pid != null && pid != -1) {
            wrapper.eq("parent_id", pid);
        }

        return this.list(wrapper);
    }

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public List<CategoryVo> queryCategoryVoByPid(Long pid) {

        return categoryMapper.queryCategoryVoByPid(pid);
    }

    @Override
    public List<ItemCategoryVo> queryCategoriesByCid3(Long cid3) {
        // 查询三级分类
        CategoryEntity categoryEntity3 = this.categoryMapper.selectById(cid3);
        ItemCategoryVo itemCategoryVo3 = new ItemCategoryVo();
        itemCategoryVo3.setCategoryId(categoryEntity3.getId());
        itemCategoryVo3.setCategoryName(categoryEntity3.getName());

        // 查询二级分类
        CategoryEntity categoryEntity2 = this.categoryMapper.selectById(categoryEntity3.getParentId());
        ItemCategoryVo itemCategoryVo2 = new ItemCategoryVo();
        itemCategoryVo2.setCategoryId(categoryEntity2.getId());
        itemCategoryVo2.setCategoryName(categoryEntity2.getName());

        // 查询一级分类
        CategoryEntity categoryEntity1 = this.categoryMapper.selectById(categoryEntity2.getParentId());
        ItemCategoryVo itemCategoryVo1 = new ItemCategoryVo();
        itemCategoryVo1.setCategoryId(categoryEntity1.getId());
        itemCategoryVo1.setCategoryName(categoryEntity1.getName());

        List<ItemCategoryVo> itemCategoryVos = new ArrayList<>();
        itemCategoryVos.add(itemCategoryVo1);
        itemCategoryVos.add(itemCategoryVo2);
        itemCategoryVos.add(itemCategoryVo3);

        return itemCategoryVos;
    }

}
