package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.mapper.SpuAttrValueMapper;
import com.atguigu.gmall.pms.vo.AttrValueVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.AttrGroupMapper;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.service.AttrGroupService;
import org.springframework.util.CollectionUtils;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupMapper, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    private AttrMapper attrMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<AttrGroupEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<GroupVo> queryGroupWithAttrsByCid(Long catId) {

        List<AttrGroupEntity> groupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", catId));

        // 判断分类下的分组是否为空
        if (CollectionUtils.isEmpty(groupEntities)){
            return null;
        }

        return groupEntities.stream().map(groupEntity -> {
            GroupVo groupVo = new GroupVo();
            BeanUtils.copyProperties(groupEntity, groupVo);

            // 查询每个组下的规格参数
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", groupEntity.getId()).eq("type", 1));
            groupVo.setAttrEntities(attrEntities);
            return groupVo;
        }).collect(Collectors.toList());

    }

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private SpuAttrValueMapper spuAttrValueMapper;

    @Override
    public List<ItemGroupVo> queryGoupsWithAttrValues(Long cid, Long spuId, Long skuId) {

        // 查询该分类下所有商品有那些规格参数分组
        List<AttrGroupEntity> groups = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", cid));
        if (CollectionUtils.isEmpty(groups)){
            return null;
        }

        return groups.stream().map(group -> {
            ItemGroupVo itemGroupVo = new ItemGroupVo();

            itemGroupVo.setGroupName(group.getName());
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", group.getId()));
            if (!CollectionUtils.isEmpty(attrEntities)){
                List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());
                // 查询所有销售属性值
                List<SkuAttrValueEntity> skuAttrValueEntities = this.skuAttrValueMapper.selectList(new QueryWrapper<SkuAttrValueEntity>().eq("sku_id", skuId).in("attr_id", attrIds));
                // 查询所有基本属性值
                List<SpuAttrValueEntity> spuAttrValueEntities = this.spuAttrValueMapper.selectList(new QueryWrapper<SpuAttrValueEntity>().eq("spu_id", spuId).in("attr_id", attrIds));

                List<AttrValueVo> attrValues = new ArrayList<>();

                // 销售属性放入组
                if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                    List<AttrValueVo> skuAttrValueVos = skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                        AttrValueVo attrValueVo = new AttrValueVo();
                        BeanUtils.copyProperties(skuAttrValueEntity, attrValueVo);
                        return attrValueVo;
                    }).collect(Collectors.toList());
                    attrValues.addAll(skuAttrValueVos);
                }

                // 基本属性放入组
                if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
                    List<AttrValueVo> spuAttrValueVos = spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                        AttrValueVo attrValueVo = new AttrValueVo();
                        BeanUtils.copyProperties(spuAttrValueEntity, attrValueVo);
                        return attrValueVo;
                    }).collect(Collectors.toList());
                    attrValues.addAll(spuAttrValueVos);
                }

                itemGroupVo.setAttrValues(attrValues);
            }
            return itemGroupVo;
        }).collect(Collectors.toList());
    }

//    public static void main(String[] args) {
//        List<User> users = Arrays.asList(
//                new User(1l, "liuyan", 20),
//                new User(2l, "xiaolu", 21),
//                new User(3l, "masu", 22),
//                new User(4l, "hanhong", 23),
//                new User(5l, "xiaosong", 24)
//        );
        // stream: map  filter  reduce
//        System.out.println(users.stream().map(User::getAge).reduce((a, b) -> a + b).get());
//        users.stream()
//                .filter(user -> user.getAge() % 2 == 0)
//                .map(user -> user.getName())
//                .collect(Collectors.toList())
//                .forEach(System.out::println);
//    }

}
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//class User {
//    private Long id;
//    private String name;
//    private Integer age;
//}
