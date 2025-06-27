package com.clls.customboot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import javax.annotation.Resource;
import java.util.List;

@SpringBootApplication
//@MapperScan(basePackages = "com.clls.customboot.dao.dynamic")
//@MapperScan(basePackages = "com.clls.customboot.dao.independent",
//        sqlSessionFactoryRef = "independentSqlSessionFactory")
public class Application {

    @Resource
    private ApplicationContext applicationContext;
    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(Application.class, args);
    }

}