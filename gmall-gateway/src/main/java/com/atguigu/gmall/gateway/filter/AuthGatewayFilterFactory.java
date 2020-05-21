package com.atguigu.gmall.gateway.filter;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilter.Config> {

    public AuthGatewayFilterFactory() {
        super(AuthGatewayFilter.Config.class);
    }

    @Override
    public GatewayFilter apply(AuthGatewayFilter.Config config) {
        return new AuthGatewayFilter(config);
    }
}
