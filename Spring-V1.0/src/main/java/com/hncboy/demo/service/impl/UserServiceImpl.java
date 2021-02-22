package com.hncboy.demo.service.impl;

import com.hncboy.demo.service.UserService;
import com.hncboy.framework.annotation.BoyService;

/**
 * @author hncboy
 * @date 2021/2/22 13:28
 * @description UserService impl
 */
@BoyService("myUserServiceImpl")
public class UserServiceImpl implements UserService {

    @Override
    public String say(String name) {
        return "hello," + name + ".I am hncboy.";
    }
}
