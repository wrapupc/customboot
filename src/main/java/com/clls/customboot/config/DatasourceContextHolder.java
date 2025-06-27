package com.clls.customboot.config;

public class DatasourceContextHolder {
    private static final ThreadLocal<DatasourceType> CONTEXT = new ThreadLocal<>();

    public static void setDatasourceType(DatasourceType type) {
        CONTEXT.set(type);
    }

    public static DatasourceType getDatasourceType() {
        return CONTEXT.get() == null ? DatasourceType.ROOT : CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
