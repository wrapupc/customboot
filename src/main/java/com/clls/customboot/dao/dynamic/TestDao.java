package com.clls.customboot.dao.dynamic;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Qualifier;

@Mapper
@Qualifier("ds1")
public interface TestDao {
    Long getMockUser(@Param("userId") Long userId);
    void updateMockUser(@Param("userId") Long userId);
}
