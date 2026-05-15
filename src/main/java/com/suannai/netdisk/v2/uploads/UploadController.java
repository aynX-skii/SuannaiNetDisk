package com.suannai.netdisk.v2.uploads;

import com.suannai.netdisk.common.api.ApiResponse;
import com.suannai.netdisk.common.util.SessionUser;
import com.suannai.netdisk.common.util.SessionUserHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<PrepareUploadResponse> prepare(@RequestBody @Validated PrepareUploadRequest request) {
        SessionUser user = SessionUserHelper.requireUser();
        return ApiResponse.ok(uploadService.prepare(user, request));
    }

    @PutMapping("/{uploadId}/parts/{partNumber}")
    public ApiResponse<Void> uploadPart(@PathVariable("uploadId") String uploadId,
                                        @PathVariable("partNumber") Integer partNumber,
                                        HttpServletRequest request) throws IOException {
        SessionUser user = SessionUserHelper.requireUser();
        uploadService.storePart(user, uploadId, partNumber, request.getInputStream().readAllBytes());
        return ApiResponse.okMessage("\u5206\u7247\u5df2\u4fdd\u5b58");
    }

    @PostMapping("/{uploadId}/complete")
    public ApiResponse<Void> complete(@PathVariable("uploadId") String uploadId) throws Exception {
        SessionUser user = SessionUserHelper.requireUser();
        uploadService.complete(user, uploadId);
        return ApiResponse.okMessage("\u4e0a\u4f20\u5b8c\u6210");
    }

    @GetMapping("/{uploadId}")
    public ApiResponse<UploadSessionView> getSession(@PathVariable("uploadId") String uploadId) {
        SessionUser user = SessionUserHelper.requireUser();
        return ApiResponse.ok(uploadService.getSession(user, uploadId));
    }

    @DeleteMapping("/{uploadId}")
    public ApiResponse<Void> cancel(@PathVariable("uploadId") String uploadId) {
        SessionUser user = SessionUserHelper.requireUser();
        uploadService.cancel(user, uploadId);
        return ApiResponse.okMessage("\u4e0a\u4f20\u5df2\u53d6\u6d88");
    }
}
