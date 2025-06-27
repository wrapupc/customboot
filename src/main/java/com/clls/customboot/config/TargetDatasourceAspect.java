package com.clls.customboot.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class TargetDatasourceAspect {

    // 定义切点
    @Pointcut("@annotation(com.clls.customboot.config.TargetDatasource)")
    public void dataSourcePointCut() {
    }

    @Around("dataSourcePointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        TargetDatasource ds = method.getAnnotation(TargetDatasource.class);

        if (ds != null) {
            DatasourceContextHolder.setDatasourceType(ds.value());
        }

        try {
            return point.proceed();
        } finally {
            DatasourceContextHolder.clear();
        }
    }
}
