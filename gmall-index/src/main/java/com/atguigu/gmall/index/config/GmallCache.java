package com.atguigu.gmall.index.config;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {

    /**
     * 缓存的前缀
     * @return
     */
    String prefix() default "";

    /**
     * 缓存的有效时间，单位：分钟
     * @return
     */
    int timeout() default 1440;

    /**
     * 防止缓存击穿，指定的分布式锁名称
     * @return
     */
    String lock() default "lock";

    /**
     * 防止缓存雪崩，设定随机值范围。单位：分钟
     * @return
     */
    int random() default 50;
}
