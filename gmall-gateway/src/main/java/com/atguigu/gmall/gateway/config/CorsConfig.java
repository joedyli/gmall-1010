package com.atguigu.gmall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(){

        // 初始化一个跨域配置类
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://manager.gmall.com");// 允许那些域名跨域访问，这里一定不要写*，写*的话不能携带cookie
        configuration.setAllowCredentials(true); // 是否可以携带cookie
        configuration.addAllowedHeader("*"); // 允许携带任何都信息跨域访问
        configuration.addAllowedMethod("*"); // 允许所有的请求方法跨域访问

        // 初始化一个跨域配置源
        UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();
        configurationSource.registerCorsConfiguration("/**", configuration);
        // 初始化一个跨域过滤器
        return new CorsWebFilter(configurationSource);
    }
}
