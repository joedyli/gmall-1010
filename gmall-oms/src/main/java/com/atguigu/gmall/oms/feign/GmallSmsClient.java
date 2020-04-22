package com.atguigu.gmall.oms.feign;

import com.atguigu.gmall.sms.api.GmallSmsApi;
import com.atguigu.gmall.ums.api.GmallUmsApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("sms-service")
public interface GmallSmsClient extends GmallSmsApi {
}
