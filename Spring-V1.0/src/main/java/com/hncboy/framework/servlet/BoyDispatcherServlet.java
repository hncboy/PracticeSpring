package com.hncboy.framework.servlet;

import com.hncboy.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author hncboy
 * @date 2021/2/22 13:23
 * @description BoyDispatcherServlet
 */
public class BoyDispatcherServlet extends HttpServlet {

    /**
     * 享元模式，缓存
     * 存放扫描到的所有类的全限定类名称
     */
    private final List<String> classNames = new ArrayList<>();

    /**
     * IOC 容器，key 默认为首字母小写的类名，value 为实例对象
     */
    private final Map<String, Object> ioc = new HashMap<>();

    /**
     * 定义的配置文件 application.properties
     */
    private final Properties contextConfig = new Properties();

    /**
     * 路径映射
     */
    private final Map<String, Method> handlerMapping = new HashMap<>();

    /**
     * 初始化 Servlet
     *
     * @param config Servlet 配置
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1.加载在 web.xml 中该 BoyDispatcher"Servlet 的配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2.对配置文件中配置要扫描的包进行扫描
        doScanPackage(contextConfig.getProperty("scanPackage"));

        // 3.初始化 IOC 容器，将扫描到的类进行实例化
        doInitInstance();

        // 4.实现 DI 依赖注入
        doAutowired();

        // 5.初始化 HandlerMapping
        doInitHandlerMapping();

        System.out.println("BoyDispatcherServlet is init.");
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            // 6.委派，根据 request 寻找对应的要调用的方法通过 response 返回
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception, Detail: " + Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * 委派，根据 request 寻找对应的要调用的方法通过 response 返回
     *
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // 获取完整的路径
        String requestUrl = req.getRequestURI();
        // 获取上下文路径
        String contextPath = req.getContextPath();

        // 将完整路径中的上下文部分替换并替换多余的斜杠
        requestUrl = requestUrl.replaceAll(contextPath, "").replaceAll("/+", "/");

        // 如果找不到该路径，则返回 404
        if (!handlerMapping.containsKey(requestUrl)) {
            resp.getWriter().write("404 not found!");
            return;
        }

        // 获取实际请求入参
        Map<String, String[]> actualParameterMap = req.getParameterMap();
        // 根据路径获取要调用的方法
        Method method = handlerMapping.get(requestUrl);
        // 获取该方法的形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 定义和方法形参长度一样的存放入参值的数组
        Object[] parameterValues = new Object[parameterTypes.length];

        // 遍历所有形参按顺序进行处理
        for (int i = 0; i < parameterTypes.length; i++) {
            // 获取形参的 Class
            Class<?> parameterType = parameterTypes[i];

            if (parameterType == HttpServletRequest.class) {
                // 参数类型为 HttpServletRequest
                parameterValues[i] = req;
            } else if (parameterType == HttpServletResponse.class) {
                // 参数类型为 HttpServletResponse
                parameterValues[i] = resp;
            } else if (parameterType == String.class) {
                // 获取该方法下参数的所有注解
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                // 遍历该二维数组
                for (Annotation[] annotations : parameterAnnotations) {
                    for (Annotation annotation : annotations) {
                        // 如果使用了 @BoyRequestParam 注解
                        if (annotation instanceof BoyRequestParam) {
                            // 取出注解中的名字
                            String paramName = ((BoyRequestParam) annotation).value();
                            if (!paramName.trim().isEmpty()) {
                                // 从实际入参列表中取出对应的参数值转为字符串，替换数组格式和空格
                                String value = Arrays.toString(actualParameterMap.get(paramName))
                                        .replaceAll("\\[|\\]", "")
                                        .replaceAll("\\s+", ",");
                                parameterValues[i] = value;
                            }
                        }
                    }
                }
            } else {
                System.out.println("参数类型：" + parameterType + " 暂时未作处理；");
            }
        }

        // 获取该方法所在类的 beanName
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        // 通过所在类的实例及方法参数调用对应方法
        method.invoke(ioc.get(beanName), parameterValues);

        System.out.println("映射路径：" + requestUrl+ " 通过反射调用方法 " + method.getName() + "成功；");
    }

    /**
     * 初始化 HandlerMapping
     */
    private void doInitHandlerMapping() {
        // 遍历所有 ioc 容器
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取 Class
            Class<?> clazz = entry.getValue().getClass();

            // 跳过没有 @BoyController 注解的类
            if (!clazz.isAnnotationPresent(BoyController.class)) {
                continue;
            }

            // Controller 类上配置的路径
            String baseUrl = "";
            // 如果该类上有 @BoyRequestMapping 注解，则拼接定义的路径
            if (clazz.isAnnotationPresent(BoyRequestMapping.class)) {
                baseUrl = clazz.getAnnotation(BoyRequestMapping.class).value();
            }

            // 遍历该类上的 public 方法
            for (Method method : clazz.getMethods()) {
                // 跳过没有 @BoyRequestMapping 注解的方法
                if (!method.isAnnotationPresent(BoyRequestMapping.class)) {
                    continue;
                }

                // 获取方法上的路径
                String methodUrl = method.getAnnotation(BoyRequestMapping.class).value();

                // 拼接完整的路径，替换路径中多余的斜杠为一个斜杠
                String url = ("/" + baseUrl + "/" + methodUrl).replaceAll("/+", "/");

                // 存入映射的路径以及对应的方法
                handlerMapping.put(url, method);

                System.out.println("映射路径：" + url + ", 映射方法：" + method.getName());
            }
        }
    }

    /**
     * 实现 DI 依赖注入
     */
    private void doAutowired() {
        try {
            // 遍历所有 ioc 容器
            for (Map.Entry<String, Object> entry : ioc.entrySet()) {
                // 遍历该类下定义的所有属性
                for (Field field : entry.getValue().getClass().getDeclaredFields()) {
                    // 跳过没有 @BoyAutowired 注解修饰的属性
                    if (!field.isAnnotationPresent(BoyAutowired.class)) {
                        continue;
                    }

                    // 暴力访问
                    field.setAccessible(true);

                    // 获取自定义的 beanName
                    String beanName = field.getAnnotation(BoyAutowired.class).value().trim();
                    // 如果没有自定义的 beanName，则取该属性的类型作为 beanName
                    if (beanName.isEmpty()) {
                        beanName = field.getType().getName();
                    }

                    // 对该属性进行赋值也就是依赖注入
                    field.set(entry.getValue(), ioc.get(beanName));

                    System.out.println(entry.getValue().getClass().getName() + " 类的 " + beanName + " 已注入；");
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("DI 依赖注入失败");
        }
    }

    /**
     * 初始化 IOC 容器，将扫描到的类进行实例化
     */
    private void doInitInstance() {
        try {
            // 遍历所有类名
            for (String className : classNames) {
                // 根据类的全限定名进行加载
                Class<?> clazz = Class.forName(className);

                // 1.如果该类包含 @BoyController 注解
                if (clazz.isAnnotationPresent(BoyController.class)) {
                    // 注解上的自定义 beanName
                    String beanName = clazz.getAnnotation(BoyController.class).value();
                    putBeanName(clazz, beanName);
                    continue;
                }

                // 2.如果该类包含 @BoyService 注解
                if (clazz.isAnnotationPresent(BoyService.class)) {
                    // 注解上的自定义 beanName
                    String beanName = clazz.getAnnotation(BoyService.class).value();
                    putBeanName(clazz, beanName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("初始化 IOC 容器失败");
        }
    }

    /**
     * 将 beanName 及实例存入 ioc 容器
     *
     * @param clazz    Class 类
     * @param beanName 注解上的自定义 beanName
     */
    private void putBeanName(Class<?> clazz, String beanName)
            throws IllegalAccessException, InstantiationException {

        // 如果自定义 beanName 为空
        if (beanName.trim().isEmpty()) {
            // 将类名的首字母改为小写作为 beanName
            beanName = toLowerFirstCase(clazz.getSimpleName());
        }

        // 如果 beanName 重复
        if (ioc.containsKey(beanName)) {
            throw new RuntimeException(beanName + " 作为 beanName 重复；");
        }

        // 对该类进行实例化存入 ioc 容器
        Object instance = clazz.newInstance();
        ioc.put(beanName, instance);

        System.out.println("beanName：" + beanName + " 实例化完毕；");

        // 遍历该类所有实现的接口，因为可以通过接口注入该类的实例
        for (Class<?> clazzInterface : clazz.getInterfaces()) {
            // 判断该接口对应的实例是否已经存在
            if (ioc.containsKey(clazzInterface.getName())) {
                throw new RuntimeException(clazzInterface.getName() + " 接口已有同名 beanName 被注入；");
            }
            ioc.put(clazzInterface.getName(), instance);
            System.out.println("beanName：" + clazzInterface.getName() + "，该接口实现类 " + beanName + " 实例化完毕；");
        }
    }

    /**
     * 将单词首字母转为小写
     *
     * @param word 单词
     * @return
     */
    private String toLowerFirstCase(String word) {
        char[] chars = word.toCharArray();
        // 如果是大写，则转小写
        if (Character.isUpperCase(chars[0])) {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }

    /**
     * 对配置文件中配置要扫描的包进行扫描
     *
     * @param scanPackage 扫描的包
     */
    private void doScanPackage(String scanPackage) {
        // 将配置的以点分割的包的路径改为以斜杠分割
        URL resource = getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        if (resource == null) {
            System.out.println(scanPackage + " 包不存在；");
            return;
        }
        // 获取该路径下的 file
        File classPath = new File(resource.getFile());
        File[] files = classPath.listFiles();
        if (files == null) {
            System.out.println(scanPackage + " 包下无 file");
            return;
        }

        // 遍历当前路径下的所有 File
        for (File file : files) {
            // 如果当前 file 为目录，则用点拼接上当前的目录名，进行递归
            if (file.isDirectory()) {
                doScanPackage(scanPackage + "." + file.getName());
                continue;
            }

            // 此时 file 为文件，如果该文件不是 class 文件，则继续循环
            if (!file.getName().endsWith(".class")) {
                continue;
            }

            // 此时 file 为 class 结尾的文件，获取该类的全限定类名存入集合
            String className = scanPackage + "." + file.getName().replace(".class", "");
            classNames.add(className);
        }
    }

    /**
     * 根据配置文件名加载 resources 下的配置文件
     *
     * @param contextConfigLocation 配置文件路径名
     */
    private void doLoadConfig(String contextConfigLocation) {
        // 获取到该 resource 所在的输入流
        try (InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(contextConfigLocation)) {
            // 根据输入流进行加载
            if (resourceAsStream == null) {
                System.out.println(contextConfigLocation + " 配置文件加载失败");
                throw new RuntimeException();
            }
            contextConfig.load(resourceAsStream);
        } catch (IOException e) {
            System.out.println(contextConfigLocation + " 配置文件加载失败");
            throw new RuntimeException();
        }
    }
}
