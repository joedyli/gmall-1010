package com.atguigu.gmall.auth.service;

import com.atguigu.gmall.auth.config.JwtProperties;
import com.atguigu.gmall.auth.feign.GmallUmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.UserException;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Service
@EnableConfigurationProperties({JwtProperties.class})
public class AuthService {

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private JwtProperties jwtProperties;

    public String accredit(String username, String password) {

        try {
            // 1. 完成远程请求，获取用户信息
            ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUser(username, password);
            UserEntity userEntity = userEntityResponseVo.getData();

            // 2. 判断用户信息是否为空
            if (userEntity == null) {
                throw new UserException("用户名或者密码有误！");
            }

            // 3. 制作jwt类型的token信息
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userEntity.getId());
            map.put("username", userEntity.getUsername());
            return JwtUtils.generateToken(map, this.jwtProperties.getPrivateKey(), this.jwtProperties.getExpire());

        } catch (Exception e) {
            e.printStackTrace();
            throw new UserException("用户名或者密码出错！");
        }
    }
}
