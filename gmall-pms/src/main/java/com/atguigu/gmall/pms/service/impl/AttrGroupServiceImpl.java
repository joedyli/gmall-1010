package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.vo.GroupVo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", groupEntity.getId()));
            groupVo.setAttrEntities(attrEntities);
            return groupVo;
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
