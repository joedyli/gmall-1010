package com.atguigu.gmall.order.feign;

import com.atguigu.gmall.order.api.GmallOmsApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("oms-service")
public interface GmallOmsClient extends GmallOmsApi {
}
