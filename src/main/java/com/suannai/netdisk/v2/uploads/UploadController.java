package com.suannai.netdisk.v2.uploads;

import com.suannai.netdisk.common.api.ApiResponse;
import com.suannai.netdisk.common.util.SessionUserHelper;
import com.suannai.netdisk.common.util.SessionUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

@Validated
@RestController
@RequestMapping("/api/v2/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/prepare")
    public ApiResponse<PrepareUploadResponse> prepare(@RequestBody @Validated PrepareUploadRequest request,
                                                      HttpSession session) {
        SessionUser user = SessionUserHelper.requireUser(session);
        return ApiResponse.ok(uploadService.prepare(user, request));
    }

    @PutMapping("/{uploadId}/parts/{partNumber}")
    public ApiResponse<Void> uploadPart(@PathVariable("uploadId") String uploadId,
                                        @PathVariable("partNumber") Integer partNumber,
                                        HttpServletRequest request,
                                        HttpSession session) throws IOException {
        SessionUser user = SessionUserHelper.requireUser(session);
        uploadService.storePart(user, uploadId, partNumber, request.getInputStream().readAllBytes());
        return ApiResponse.okMessage("分片已保存");
    }

    @PostMapping("/{uploadId}/complete")
    public ApiResponse<Void> complete(@PathVariable("uploadId") String uploadId, HttpSession session) throws Exception {
        SessionUser user = SessionUserHelper.requireUser(session);
        uploadService.complete(user, uploadId);
        return ApiResponse.okMessage("上传完成");
    }

    @GetMapping("/{uploadId}")
    public ApiResponse<UploadSessionView> getSession(@PathVariable("uploadId") String uploadId, HttpSession session) {
        SessionUser user = SessionUserHelper.requireUser(session);
        return ApiResponse.ok(uploadService.getSession(user, uploadId));
    }

    @DeleteMapping("/{uploadId}")
    public ApiResponse<Void> cancel(@PathVariable("uploadId") String uploadId, HttpSession session) {
        SessionUser user = SessionUserHelper.requireUser(session);
        uploadService.cancel(user, uploadId);
        return ApiResponse.okMessage("上传已取消");
    }
}
