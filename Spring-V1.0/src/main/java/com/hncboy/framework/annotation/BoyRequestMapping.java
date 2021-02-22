package com.hncboy.framework.annotation;

import java.lang.annotation.*;

/**
 * @author hncboy
 * @date 2021/2/22 13:30
 * @description 用于定义 Controller 中映射路径的注解
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BoyRequestMapping {

    String value() default "";
}
