package com.atguigu.gmall.gateway.filter;

import com.atguigu.gmall.common.utils.IpUtil;
import com.atguigu.gmall.common.utils.JwtUtil;
import com.atguigu.gmall.gateway.config.JwtProperties;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@EnableConfigurationProperties({JwtProperties.class})
public class AuthGatewayFilter implements GatewayFilter {

    @Autowired
    private JwtProperties properties;

    private Config config;

    public AuthGatewayFilter(Config config){
        super();
        this.config = config;
    }

    /**
     * 编写过滤器业务逻辑
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // 获取request和response，注意：不是HttpServletRequest及HttpServletResponse
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        // 获取当前请求的path路径
        String path = request.getURI().getPath();

        // 1.请求路径经不在拦截名单中，直接放行
        Boolean flag = false;
//        for (String authUrl : this.config.getAuthUrls()) {
//            // 如果白名单中有一个包含当前路径
//            if (path.indexOf(authUrl) != -1){
//                flag = true;
//                break;
//            }
//        }
        // 不在拦截名单中，放行
        if (!flag){
            return chain.filter(exchange);
        }

        // 2.判断是否内部接口
        // 匹配路径的工具类
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        // 如果是内部接口，则网关拦截不允许外部访问！
        if (antPathMatcher.match("/**/inner/**",path)){
            // 重定向到登录
            // 303状态码表示由于请求对应的资源存在着另一个URI，应使用重定向获取请求的资源
            response.setStatusCode(HttpStatus.SEE_OTHER);
            response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl="+request.getURI());
            return response.setComplete();
        }

        // 3.获取请求中的token
        String token = "";
        // 异步请求，通过头信息获取token
        List<String> tokenList = request.getHeaders().get("token");
        if(!CollectionUtils.isEmpty(tokenList)) {
            token = tokenList.get(0);
        } else {
            // 同步请求通过cookie
            MultiValueMap<String, HttpCookie> cookies = request.getCookies();
            if (CollectionUtils.isEmpty(cookies) || !cookies.containsKey(properties.getCookieName())) {
                // 拦截
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }

            // 获取cookie中的jwt
            HttpCookie cookie = cookies.getFirst(properties.getCookieName());
            token = cookie.getValue();
        }

        // 4.判断token是否为空
        if (StringUtils.isEmpty(token)) {
            // 拦截
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        try {
            // 5.解析jwt，获取登录信息
            Map<String, Object> map = JwtUtil.getInfoFromToken(token, this.properties.getPublicKey());

            // 6.判断token是否被盗用
            String ip = map.get("ip").toString();
            // 当前请求的ip
            String curIp = IpUtil.getIpAddressAtGateway(request);
            if (!StringUtils.equals(ip, curIp)){
                // 拦截
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }

            // 7.传递登录信息给后续服务
            String userId = map.get("userId").toString();
            // 将userId转变成request对象。mutate：转变的意思
            request.mutate().header("userId", userId).build();
            exchange.mutate().request(request).build();

            // 放行
            return chain.filter(exchange);
        } catch (Exception e) {
            e.printStackTrace();
            // 4.异常，登录
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
    }

    @Data
    public static class Config {

        private String name;
        private String value;
    }
}
