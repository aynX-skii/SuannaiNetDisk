package com.suannai.netdisk.v2.profile;

import com.suannai.netdisk.common.api.ApiResponse;
import com.suannai.netdisk.common.util.SessionUserHelper;
import com.suannai.netdisk.model.User;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;

@Validated
@RestController
@RequestMapping("/api/v2")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileView> me(HttpSession session) {
        User user = SessionUserHelper.requireUser(session);
        return ApiResponse.ok(profileService.toView(user));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileView> updateProfile(@org.springframework.web.bind.annotation.RequestBody @Validated UpdateProfileRequest request,
                                                      HttpSession session) {
        User user = SessionUserHelper.requireUser(session);
        return ApiResponse.ok(profileService.updateProfile(user, request.getNickname(), session));
    }

    @PostMapping("/me/avatar")
    public ApiResponse<UserProfileView> uploadAvatar(@RequestPart("file") MultipartFile file, HttpSession session) throws Exception {
        User user = SessionUserHelper.requireUser(session);
        return ApiResponse.ok(profileService.updateAvatar(user, file, session));
    }
}
