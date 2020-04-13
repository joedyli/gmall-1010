package com.atguigu.gmall.search.listener;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.bean.Goods;
import com.atguigu.gmall.search.bean.SearchAttrValue;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ItemListener {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "search-item-queue", durable = "true"),
            exchange = @Exchange(value = "pms-item-exchange", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"item.insert", "item.update"}
    ))
    public void listener(Long spuId, Channel channel, Message message) throws IOException {
        // 2.查询spu下的sku集合
        ResponseVo<List<SkuEntity>> skuListResponseVo = this.pmsClient.querySkusBySpuId(spuId);
        List<SkuEntity> skuEntities = skuListResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuEntities)) {
            // 3.sku集合转化成goods
            List<Goods> goodsList = skuEntities.stream().map(sku -> {
                Goods goods = new Goods();
                goods.setSkuId(sku.getId());
                goods.setTitle(sku.getTitle());
                goods.setPrice(sku.getPrice().doubleValue());
                goods.setDefaultImage(sku.getDefaultImage());

                // 查询品牌相关数据
                ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(sku.getBrandId());
                BrandEntity brandEntity = brandEntityResponseVo.getData();
                if (brandEntity != null) {
                    goods.setBrandId(sku.getBrandId());
                    goods.setBrandName(brandEntity.getName());
                    goods.setLogo(brandEntity.getLogo());
                }

                // 查询分类相关数据
                ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(sku.getCategoryId());
                CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                if (categoryEntity != null) {
                    goods.setCategoryId(sku.getCategoryId());
                    goods.setCategoryName(categoryEntity.getName());
                }

                // 根据spuId查询spu信息
                ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(spuId);
                SpuEntity spuEntity = spuEntityResponseVo.getData();
                if (spuEntity != null){
                    goods.setCreateTime(spuEntity.getCreateTime());
                }

                // 查询sku对应商品库存的相关信息
                ResponseVo<List<WareSkuEntity>> wareSkuListResponseVo = this.wmsClient.queryWareSkusBySkuId(sku.getId());
                List<WareSkuEntity> wareSkuEntities = wareSkuListResponseVo.getData();
                if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                    goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a, b) -> a + b).get());
                    goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
                }

                // 先查询出检索属性
                ResponseVo<List<AttrEntity>> attrResponseVo = this.pmsClient.queryAttrsByCid(sku.getCategoryId(), null, 1);
                List<AttrEntity> attrEntities = attrResponseVo.getData();
                if (!CollectionUtils.isEmpty(attrEntities)) {
                    List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());

                    List<SearchAttrValue> searchAttrValues = new ArrayList<>();
                    ResponseVo<List<SkuAttrValueEntity>> skuAttrValuesResponseVo = this.pmsClient.querySkuSearchAttrValue(sku.getId(), attrIds);
                    ResponseVo<List<SpuAttrValueEntity>> spuAttrValuesResponseVo = this.pmsClient.querySpuSearchAttrValue(spuEntity.getId(), attrIds);
                    List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValuesResponseVo.getData();
                    List<SpuAttrValueEntity> spuAttrValueEntities = spuAttrValuesResponseVo.getData();
                    if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                        searchAttrValues.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                            SearchAttrValue searchAttrValue = new SearchAttrValue();
                            BeanUtils.copyProperties(skuAttrValueEntity, searchAttrValue);
                            return searchAttrValue;
                        }).collect(Collectors.toList()));
                    }
                    if (!CollectionUtils.isEmpty(spuAttrValueEntities)) {
                        searchAttrValues.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                            SearchAttrValue searchAttrValue = new SearchAttrValue();
                            BeanUtils.copyProperties(spuAttrValueEntity, searchAttrValue);
                            return searchAttrValue;
                        }).collect(Collectors.toList()));
                    }
                    goods.setSearchAttrs(searchAttrValues);
                }
                return goods;
            }).collect(Collectors.toList());

            this.goodsRepository.saveAll(goodsList);

        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
