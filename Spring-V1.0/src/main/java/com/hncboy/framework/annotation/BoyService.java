package com.hncboy.framework.annotation;

import java.lang.annotation.*;

/**
 * @author hncboy
 * @date 2021/2/22 13:30
 * @description 用于注入 Service 类的注解
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BoyService {

    String value() default "";
}
