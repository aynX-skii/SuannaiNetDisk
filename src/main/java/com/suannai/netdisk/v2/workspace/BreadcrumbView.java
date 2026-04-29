package com.suannai.netdisk.v2.workspace;

public class BreadcrumbView {
    private Integer id;
    private String name;

    public BreadcrumbView() {
    }

    public BreadcrumbView(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
