package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
	private static final String pubKeyPath = "D:\\project-1010\\rsa\\rsa.pub";
    private static final String priKeyPath = "D:\\project-1010\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 2);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE1ODcxMTE4Njh9.AIhhw4PnZ2n9lfArTZ3qOVTHFFpyr6oVcP5l1gNn752_siYTCRO2OQrl9V-scE7FN2oT9Dx999ofnFBiK56Lll_kunfKAqtpPQ8tJgnw1I5KRPELjWxH_9lbOrXOYvVB_w6JSZFJ_tgMtxkdBjWd7ic7-inTbyIKsGky7KITvzjOMQpuGw9oNlcKcYM6WE8Lnp3B1U-0P4ydxJI8h40sJAeRYb5cmbdLwMwFs1McWxrh2TlG51wArCIOkbn2OjO01PcjPhiSvOC3jq_IiiVbWQhcsHXz4Ih8uONKJkLQF2usqVs7Rw_gbUUAGQs0dqxNyYEpVJxWnzcl4A28W9E5WQ";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}
