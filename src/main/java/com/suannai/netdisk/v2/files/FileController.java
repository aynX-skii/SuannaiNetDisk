package com.suannai.netdisk.v2.files;

import com.suannai.netdisk.common.api.ApiResponse;
import com.suannai.netdisk.common.util.SessionUser;
import com.suannai.netdisk.common.util.SessionUserHelper;
import com.suannai.netdisk.v2.workspace.DirectoryView;
import com.suannai.netdisk.v2.workspace.FileEntryView;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2")
public class FileController {
    private final WorkspaceService workspaceService;

    public FileController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/files")
    public ApiResponse<DirectoryView> listFiles(@RequestParam(value = "parentId", required = false) Long parentId) {
        SessionUser user = SessionUserHelper.requireUser();
        return ApiResponse.ok(workspaceService.loadDirectory(user, parentId));
    }

    @GetMapping("/files/{id}")
    public ApiResponse<FileEntryView> getFile(@PathVariable("id") Long id) {
        SessionUser user = SessionUserHelper.requireUser();
        return ApiResponse.ok(workspaceService.getEntry(user, id));
    }

    @PostMapping("/directories")
    public ApiResponse<FileEntryView> createDirectory(@RequestBody @Validated CreateDirectoryRequest request) {
        SessionUser user = SessionUserHelper.requireUser();
        return ApiResponse.ok(workspaceService.createDirectory(user, request.getParentId(), request.getName()));
    }

    @PostMapping("/files/move")
    public ApiResponse<Void> moveFiles(@RequestBody @Validated MoveFilesRequest request) {
        SessionUser user = SessionUserHelper.requireUser();
        workspaceService.move(user, request.getIds(), request.getTargetParentId());
        return ApiResponse.okMessage("\u79fb\u52a8\u6210\u529f");
    }

    @DeleteMapping("/files/{id}")
    public ApiResponse<Void> deleteFile(@PathVariable("id") Long id) {
        SessionUser user = SessionUserHelper.requireUser();
        workspaceService.delete(user, id);
        return ApiResponse.okMessage("\u5220\u9664\u6210\u529f");
    }

    @GetMapping("/files/{id}/download")
    public void download(@PathVariable("id") Long id,
                         @RequestParam(value = "inline", defaultValue = "false") boolean inline,
                         HttpServletResponse response) throws Exception {
        SessionUser user = SessionUserHelper.requireUser();
        workspaceService.streamDownload(user, id, inline, response);
    }
}
