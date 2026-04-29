package com.suannai.netdisk.v2.files;

import com.suannai.netdisk.common.api.ApiResponse;
import com.suannai.netdisk.common.util.SessionUserHelper;
import com.suannai.netdisk.model.User;
import com.suannai.netdisk.v2.workspace.DirectoryView;
import com.suannai.netdisk.v2.workspace.FileEntryView;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Validated
@RestController
@RequestMapping("/api/v2")
public class FileController {
    private final WorkspaceService workspaceService;

    public FileController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/files")
    public ApiResponse<DirectoryView> listFiles(@RequestParam(value = "parentId", required = false) Integer parentId,
                                                HttpSession session) {
        User user = SessionUserHelper.requireUser(session);
        return ApiResponse.ok(workspaceService.loadDirectory(user, parentId));
    }

    @GetMapping("/files/{id}")
    public ApiResponse<FileEntryView> getFile(@PathVariable("id") Integer id, HttpSession session) {
        User user = SessionUserHelper.requireUser(session);
        return ApiResponse.ok(workspaceService.getEntry(user, id));
    }

    @PostMapping("/directories")
    public ApiResponse<FileEntryView> createDirectory(@RequestBody @Validated CreateDirectoryRequest request,
                                                      HttpSession session) {
        User user = SessionUserHelper.requireUser(session);
        return ApiResponse.ok(workspaceService.createDirectory(user, request.getParentId(), request.getName()));
    }

    @PostMapping("/files/move")
    public ApiResponse<Void> moveFiles(@RequestBody @Validated MoveFilesRequest request, HttpSession session) {
        User user = SessionUserHelper.requireUser(session);
        workspaceService.move(user, request.getIds(), request.getTargetParentId());
        return ApiResponse.okMessage("移动成功");
    }

    @DeleteMapping("/files/{id}")
    public ApiResponse<Void> deleteFile(@PathVariable("id") Integer id, HttpSession session) {
        User user = SessionUserHelper.requireUser(session);
        workspaceService.delete(user, id);
        return ApiResponse.okMessage("删除成功");
    }

    @GetMapping("/files/{id}/download")
    public void download(@PathVariable("id") Integer id,
                         @RequestParam(value = "inline", defaultValue = "false") boolean inline,
                         HttpSession session,
                         HttpServletResponse response) throws Exception {
        User user = SessionUserHelper.requireUser(session);
        workspaceService.streamDownload(user, id, inline, response);
    }
}
