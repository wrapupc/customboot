package com.clls.customboot.dao.independent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Qualifier;

@Mapper
@Qualifier("independent")
public interface IndependentDao {
    Long getMockUser(@Param("userId") Long userId);
    void insertMockUser(@Param("userId") Long userId);
}
