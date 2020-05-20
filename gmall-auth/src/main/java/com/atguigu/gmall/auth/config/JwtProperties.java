package com.atguigu.gmall.auth.config;

import com.atguigu.gmall.common.utils.RsaUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.io.File;
import java.security.PrivateKey;
import java.security.PublicKey;

@Data
@Slf4j
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    private String pubKeyPath;
    private String priKeyPath;
    private String secret;
    private String cookieName;
    private Integer expire;
    private String unick;

    private PublicKey publicKey;
    private PrivateKey privateKey;

    /**
     * 该方法在构造方法执行之后执行
     */
    @PostConstruct
    public void init(){
        try {
            File pubFile = new File(pubKeyPath);
            File priFile = new File(priKeyPath);
            // 如果公钥或者私钥不存在，重新生成公钥和私钥
            if (!pubFile.exists() || !priFile.exists()) {
                RsaUtil.generateKey(pubKeyPath, priKeyPath, secret);
            }
            this.publicKey = RsaUtil.getPublicKey(pubKeyPath);
            this.privateKey = RsaUtil.getPrivateKey(priKeyPath);
        } catch (Exception e) {
            log.error("生成公钥和私钥出错");
            e.printStackTrace();
        }
    }
}
