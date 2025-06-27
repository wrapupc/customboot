package com.clls.customboot.controller;

import com.clls.customboot.config.DatasourceType;
import com.clls.customboot.config.TargetDatasource;
import com.clls.customboot.service.MainService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class MainController {
    @Resource
    MainService mainService;

    @RequestMapping("/getUser")
//    @TargetDatasource(DatasourceType.ROOT)
    public Long getUser() {

        return mainService.getUser();
    }
}
