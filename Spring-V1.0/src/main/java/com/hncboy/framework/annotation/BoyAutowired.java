package com.hncboy.framework.annotation;

import java.lang.annotation.*;

/**
 * @author hncboy
 * @date 2021/2/22 13:30
 * @description 用于注入类中的属性
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BoyAutowired {

    String value() default "";
}
