package com.atguigu.gmall.gateway.filter;

import com.atguigu.gmall.common.utils.IpUtil;
import com.atguigu.gmall.common.utils.JwtUtil;
import com.atguigu.gmall.gateway.config.JwtProperties;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@EnableConfigurationProperties({JwtProperties.class})
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {

    @Autowired
    private JwtProperties properties;

    /**
     * 一定要重写构造方法
     * 告诉父类，这里使用PathConfig对象接收配置内容
     */
    public AuthGatewayFilterFactory() {
        super(PathConfig.class);
    }

    @Override
    public GatewayFilter apply(PathConfig config) {
        // 实现GatewaFilter接口
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

                // 获取request和response，注意：不是HttpServletRequest及HttpServletResponse
                ServerHttpRequest request = exchange.getRequest();
                ServerHttpResponse response = exchange.getResponse();
                // 获取当前请求的path路径
                String path = request.getURI().getPath();

                // 1.判断请求路径在不在拦截名单中，不在直接放行
                Boolean flag = false;
                for (String authPath : config.getAuthPaths()) {
                    // 如果白名单中有一个包含当前路径
                    if (path.indexOf(authPath) != -1){
                        flag = true;
                        break;
                    }
                }
                // 不在拦截名单中，放行
                if (!flag){
                    return chain.filter(exchange);
                }

                // 2.获取请求中的token
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
                        // 重定向到登录
                        // 303状态码表示由于请求对应的资源存在着另一个URI，应使用重定向获取请求的资源
                        response.setStatusCode(HttpStatus.SEE_OTHER);
                        response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl="+request.getURI());
                        // 设置响应状态码为未认证
//                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return response.setComplete();
                    }

                    // 获取cookie中的jwt
                    HttpCookie cookie = cookies.getFirst(properties.getCookieName());
                    token = cookie.getValue();
                }

                // 3.判断token是否为空
                if (StringUtils.isEmpty(token)) {
                    // 去登录
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl="+request.getURI());
                    return response.setComplete();
                }

                try {
                    // 4.解析jwt，获取登录信息
                    Map<String, Object> map = JwtUtil.getInfoFromToken(token, properties.getPublicKey());

                    // 5.判断token是否被盗用
                    String ip = map.get("ip").toString();
                    // 当前请求的ip
                    String curIp = IpUtil.getIpAddressAtGateway(request);
                    if (!StringUtils.equals(ip, curIp)){
                        // 去登陆
                        response.setStatusCode(HttpStatus.SEE_OTHER);
                        response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl="+request.getURI());
                        return response.setComplete();
                    }

                    // 6.传递登录信息给后续服务
                    String userId = map.get("userId").toString();
                    // 将userId转变成request对象。mutate：转变的意思
                    request.mutate().header("userId", userId).build();
                    exchange.mutate().request(request).build();

                    // 放行
                    return chain.filter(exchange);
                } catch (Exception e) {
                    e.printStackTrace();
                    // 7.异常，去登录
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl="+request.getURI());
                    return response.setComplete();
                }
            }
        };
    }

    /**
     * 指定字段顺序
     * 可以通过不同的字段分别读取：/toLogin.html,/login
     * 在这里希望通过一个集合字段读取所有的路径
     * @return
     */
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("authPaths");
    }

    /**
     * 指定读取字段的结果集类型
     * 默认通过map的方式，把配置读取到不同字段
     *  例如：/toLogin.html,/login
     *      由于只指定了一个字段，只能接收/toLogin.html
     * @return
     */
    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    /**
     * 读取配置的内部类
     */
    @Data
    public static class PathConfig{
        private List<String> authPaths;
    }
}
