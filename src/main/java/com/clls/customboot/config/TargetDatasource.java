package com.clls.customboot.config;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TargetDatasource {
    DatasourceType value() default DatasourceType.ROOT;
}
