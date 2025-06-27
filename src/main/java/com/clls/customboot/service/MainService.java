package com.clls.customboot.service;

import com.clls.customboot.dao.dynamic.TestDao;
import com.clls.customboot.dao.independent.IndependentDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class MainService {
    @Resource
    TestDao testDao;
    @Resource
    IndependentDao independentDao;


    @Transactional(rollbackFor = Exception.class)
    public Long getUser() {
//        testDao.updateMockUser(1L);
//        int a = 1 / 0;
        independentDao.getMockUser(1L);
        independentDao.insertMockUser(1L);
        int a = 1 / 0;
        return testDao.getMockUser(1L);
    }
}
