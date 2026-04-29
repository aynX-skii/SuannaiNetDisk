package com.suannai.netdisk.v2.auth;

import com.suannai.netdisk.common.api.ApiResponse;
import com.suannai.netdisk.v2.profile.UserProfileView;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Validated
@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<UserProfileView> login(@RequestBody @Validated LoginRequest request,
                                              HttpServletRequest httpServletRequest,
                                              HttpSession session) {
        return ApiResponse.ok(authService.login(request, httpServletRequest, session));
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileView> register(@RequestBody @Validated RegisterRequest request,
                                                 HttpSession session) {
        return ApiResponse.ok(authService.register(request, session));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        authService.logout(session);
        return ApiResponse.okMessage("已退出登录");
    }
}
