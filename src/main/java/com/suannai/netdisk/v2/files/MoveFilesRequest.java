package com.suannai.netdisk.v2.files;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

public class MoveFilesRequest {
    @NotEmpty
    private List<Integer> ids;
    @NotNull
    private Integer targetParentId;

    public List<Integer> getIds() {
        return ids;
    }

    public void setIds(List<Integer> ids) {
        this.ids = ids;
    }

    public Integer getTargetParentId() {
        return targetParentId;
    }

    public void setTargetParentId(Integer targetParentId) {
        this.targetParentId = targetParentId;
    }
}
