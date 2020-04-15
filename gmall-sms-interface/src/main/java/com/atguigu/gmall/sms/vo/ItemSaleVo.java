package com.atguigu.gmall.sms.vo;

import lombok.Data;

@Data
public class ItemSaleVo {
    private String type; // 积分 满减 打折
    private String desc; // 描述信息
}
