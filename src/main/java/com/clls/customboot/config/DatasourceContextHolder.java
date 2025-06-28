package com.clls.customboot.config;

public class DatasourceContextHolder {
    private static final ThreadLocal<DatasourceType> CONTEXT = new ThreadLocal<>();

    public static void setDatasourceType(DatasourceType type) {
        CONTEXT.set(type);
    }

    public static String getDatasourceType() {
        return CONTEXT.get() == null ? DatasourceType.ROOT.getValue() : CONTEXT.get().getValue();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
