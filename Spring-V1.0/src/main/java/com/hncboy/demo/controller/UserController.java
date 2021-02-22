package com.hncboy.demo.controller;

import com.hncboy.demo.service.UserService;
import com.hncboy.framework.annotation.BoyAutowired;
import com.hncboy.framework.annotation.BoyController;
import com.hncboy.framework.annotation.BoyRequestMapping;
import com.hncboy.framework.annotation.BoyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author hncboy
 * @date 2021/2/22 13:33
 * @description UserController
 */
@BoyController
@BoyRequestMapping("/demo")
public class UserController {

    @BoyAutowired
    private UserService userService;

    /**
     * 直接 say
     * @param req
     * @param resp
     * @param name
     */
    @BoyRequestMapping("/directSay")
    public void directSay(HttpServletRequest req, HttpServletResponse resp,
                          @BoyRequestParam("name") String name){
        String result = userService.say(name);
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 优雅的 say
     * @param req
     * @param resp
     * @param name
     */
    @BoyRequestMapping("/elegantSay")
    public void elegantSay(HttpServletRequest req, HttpServletResponse resp,
                          @BoyRequestParam("name") String name){
        String result = userService.say(" dear " + name);
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
