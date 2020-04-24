package com.atguigu.gmall.order.interceptor;

import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.order.config.JwtProperties;
import com.atguigu.gmall.order.vo.UserInfo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties({JwtProperties.class})
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties properties;

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

//    public static UserInfo userInfo;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UserInfo userInfo = new UserInfo();

        try {
            // 1. 获取cookie信息（userKey token）
            String token = CookieUtils.getCookieValue(request, this.properties.getCookieName());

            // 4. 解析jwt类型的token，获取用户信息（userId）
            Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.properties.getPublicKey());
            Long userId = Long.valueOf(map.get("userId").toString());

            // 5. 把userKey和userId传递给后续的业务逻辑（controller service map） TODO
            userInfo.setUserId(userId);
//        request.setAttribute("userId", userId);
            THREAD_LOCAL.set(userInfo);

            // 不管有没有登录都要放行，登录：获取userId；没有登录：获取userKey
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 如果解析过程中出现异常，拦截
//        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return true;
    }

    /**
     * 封装了一个获取线程局部变量值的静态方法
     * @return
     */
    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    /**
     * 在视图渲染完成之后执行，经常在完成方法中释放资源
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

        // 调用删除方法，是必须选项。因为使用的是tomcat线程池，请求结束后，线程不会结束。
        // 如果不手动删除线程变量，可能会导致内存泄漏
        THREAD_LOCAL.remove();
    }
}
