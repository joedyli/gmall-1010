package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.search.bean.Goods;
import com.atguigu.gmall.search.bean.SearchParamVo;
import com.atguigu.gmall.search.bean.SearchResponseAttrVo;
import com.atguigu.gmall.search.bean.SearchResponseVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVo search(SearchParamVo paramVo) {

        try {
            // 构建查询条件
            SearchRequest searchRequest = new SearchRequest(new String[]{"goods"}, this.buildDsl(paramVo));
            // 执行查询
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            // 解析结果集
            SearchResponseVo responseVo = this.parseResult(searchResponse);
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());
            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解析搜索结果集
     * @param response
     * @return
     */
    private SearchResponseVo parseResult(SearchResponse response) {
        SearchResponseVo responseVo = new SearchResponseVo();

        SearchHits hits = response.getHits();
        // 总命中的记录数
        responseVo.setTotal(hits.getTotalHits());

        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Stream.of(hitsHits).map(hitsHit -> {
            // 获取内层hits的_source 数据
            String goodsJson = hitsHit.getSourceAsString();
            // 反序列化为goods对象
            Goods goods = JSON.parseObject(goodsJson, Goods.class);

            // 获取高亮的title覆盖掉普通title
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            String highlightTitle = highlightField.getFragments()[0].toString();
            goods.setTitle(highlightTitle);
            return goods;
        }).collect(Collectors.toList());
        responseVo.setData(goodsList);

        // 聚合结果集的解析
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        // 1. 解析聚合结果集，获取品牌》
        // {attrId: null, attrName: "品牌"， attrValues: [{id: 1, name: 尚硅谷, logo: http://www.atguigu.com/logo.gif}, {}]}
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggregationMap.get("brandIdAgg");
        List<? extends Terms.Bucket> buckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            List<String> attrValues = buckets.stream().map(bucket -> { // {id: 1, name: 尚硅谷, logo: http://www.atguigu.com/logo.gif}
                // 为了得到指定格式的json字符串，创建了一个map
                Map<String, Object> map = new HashMap<>();
                // 获取brandIdAgg中的key，这个key就是品牌的id
                Long brandId = ((Terms.Bucket) bucket).getKeyAsNumber().longValue();
                map.put("id", brandId);
                // 解析品牌名称的子聚合，获取品牌名称
                Map<String, Aggregation> brandAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                ParsedStringTerms brandNameAgg = (ParsedStringTerms)brandAggregationMap.get("brandNameAgg");
                map.put("name", brandNameAgg.getBuckets().get(0).getKeyAsString());
                // 解析品牌logo的子聚合，获取品牌 的logo
                ParsedStringTerms logoAgg = (ParsedStringTerms)brandAggregationMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)){
                    map.put("logo", logoAggBuckets.get(0).getKeyAsString());
                }
                // 把map反序列化为json字符串
                return JSON.toJSONString(map);
            }).collect(Collectors.toList());
            SearchResponseAttrVo brand = new SearchResponseAttrVo();
            brand.setAttrId(null);
            brand.setAttrName("品牌");
            brand.setAttrValues(attrValues);
            responseVo.setBrand(brand);
        }

        // 2. 解析聚合结果集，获取分类
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms)aggregationMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryIdAggBuckets)){
            List<String> attrValues = categoryIdAggBuckets.stream().map(bucket -> { // {id: 225, name: 手机}
                Map<String, Object> map = new HashMap<>();
                // 获取bucket的key，key就是分类的id
                Long categoryId = ((Terms.Bucket) bucket).getKeyAsNumber().longValue();
                map.put("id", categoryId);
                // 解析分类名称的子聚合，获取分类名称
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                map.put("name", categoryNameAgg.getBuckets().get(0).getKeyAsString());
                return JSON.toJSONString(map);
            }).collect(Collectors.toList());
            SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
            searchResponseAttrVo.setAttrId(null);
            searchResponseAttrVo.setAttrName("分类");
            searchResponseAttrVo.setAttrValues(attrValues);
            responseVo.setCategory(searchResponseAttrVo);
        }

        // 3. 解析聚合结果集，获取规格参数
        ParsedNested attrAgg = (ParsedNested)aggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)) {
            List<SearchResponseAttrVo> filters = attrIdAggBuckets.stream().map(bucket -> {
                SearchResponseAttrVo responseAttrVo = new SearchResponseAttrVo();
                // 规格参数id
                responseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                // 规格参数的名称
                ParsedStringTerms attrNameAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
                responseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());
                // 规格参数值
                ParsedStringTerms attrValueAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrValueAggBuckets)){
                    List<String> attrValues = attrValueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                    responseAttrVo.setAttrValues(attrValues);
                }
                return responseAttrVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(filters);
        }

        return responseVo;
    }

    /**
     * 构建查询DSL语句
     * @return
     */
    private SearchSourceBuilder buildDsl(SearchParamVo paramVo) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isEmpty(keyword)){
            // 打广告，TODO
            return null;
        }

        // 1. 构建查询条件（bool查询）
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 1.1. 匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));
        // 1.2. 过滤
        // 1.2.1. 品牌过滤
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }
        // 1.2.2. 分类过滤
        Long cid = paramVo.getCid();
        if (cid != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("categoryId", cid));
        }

        // 1.2.3. 价格区间过滤
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        if (priceFrom != null || priceTo != null){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (priceFrom != null){
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null) {
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }

        // 1.2.4. 是否有货
        Boolean store = paramVo.getStore();
        if (store != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }

        // 1.2.5. 规格参数的过滤 props=5:高通-麒麟,6:骁龙865-硅谷1000
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)){
            props.forEach(prop -> {
                String[] attrs = StringUtils.split(prop, ":");
                if (attrs != null && attrs.length == 2) {
                    String attrId = attrs[0];
                    String attrValueString = attrs[1];
                    String[] attrValues = StringUtils.split(attrValueString, "-");

                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attrId));
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", attrValues));
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));
                }
            });
        }
        sourceBuilder.query(boolQueryBuilder);

        // 2. 构建排序
        String sort = paramVo.getSort();
        if (StringUtils.isNotBlank(sort)){
            String[] sorts = StringUtils.split(sort, ":");
            if (sorts != null && sorts.length == 2){
                String field = "";
                switch (sorts[0]){
                    case "1": field = "price"; break;
                    case "2": field = "createTime"; break;
                    case "3": field = "sales"; break;
                }
                sourceBuilder.sort(field, StringUtils.equals(sorts[1], "desc") ? SortOrder.DESC : SortOrder.ASC);
            }
        }

        // 3. 构建分页
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);

        // 4. 构建高亮
        sourceBuilder.highlighter(new HighlightBuilder().field("title").preTags("<font style='color:red'>").postTags("</font>"));

        // 5. 构建聚合
        // 5.1. 构建品牌聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                .subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));

        // 5.2. 构建分类聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));

        // 5.3. 构建规格参数的嵌套聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "searchAttrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))));

        // 6. 构建结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId", "title", "price", "defaultImage"}, null);

        System.out.println(sourceBuilder.toString());
        return sourceBuilder;
    }
}
