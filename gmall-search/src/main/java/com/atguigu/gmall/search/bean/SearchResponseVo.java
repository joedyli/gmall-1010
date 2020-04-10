package com.atguigu.gmall.search.bean;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {
    // 过滤
    // {attrId: null, attrName: "品牌"， attrValues: [{id: 1, name: 尚硅谷, logo: http://www.atguigu.com/logo.gif}, {}]}
    private SearchResponseAttrVo brand;
    // {attrId: null, attrName: "分类"， attrValues: [{id: 225, name: 手机}, {id: 250, name: 啥子}]}
    private SearchResponseAttrVo category;
    // 规格：[{attrId: 8, attrName: "内存", attrValues: ["8G", "12G"]}, {attrId: 9, attrName: "机身存储", attrValues: ["128G", "256G"]}]
    private List<SearchResponseAttrVo> filters;

    // 分页
    private Integer pageNum;
    private Integer pageSize;
    private Long total;

    // 当前页数据
    private List<Goods> data;
}
