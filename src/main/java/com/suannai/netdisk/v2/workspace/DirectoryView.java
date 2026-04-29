package com.suannai.netdisk.v2.workspace;

import java.util.List;

public class DirectoryView {
    private FileEntryView current;
    private List<BreadcrumbView> breadcrumbs;
    private List<FileEntryView> items;

    public FileEntryView getCurrent() {
        return current;
    }

    public void setCurrent(FileEntryView current) {
        this.current = current;
    }

    public List<BreadcrumbView> getBreadcrumbs() {
        return breadcrumbs;
    }

    public void setBreadcrumbs(List<BreadcrumbView> breadcrumbs) {
        this.breadcrumbs = breadcrumbs;
    }

    public List<FileEntryView> getItems() {
        return items;
    }

    public void setItems(List<FileEntryView> items) {
        this.items = items;
    }
}
