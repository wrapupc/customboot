package com.clls.customboot.dao.independent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndependentDao {
    Long getMockUser(@Param("userId") Long userId);
    void insertMockUser(@Param("userId") Long userId);
}
