package com.hncboy.framework.annotation;

import java.lang.annotation.*;

/**
 * @author hncboy
 * @date 2021/2/22 13:30
 * @description 用于定义 Controller 中入参
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BoyRequestParam {

    String value() default "";
}
