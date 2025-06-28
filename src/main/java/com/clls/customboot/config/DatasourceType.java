package com.clls.customboot.config;

public enum DatasourceType {
    ROOT("root"),RO("ro"),INDEPENDENT("independent");

    private String value;


    DatasourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
