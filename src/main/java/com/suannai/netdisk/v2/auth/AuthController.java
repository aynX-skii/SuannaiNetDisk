package com.suannai.netdisk.v2.auth;

import com.suannai.netdisk.common.api.ApiResponse;
import com.suannai.netdisk.v2.profile.UserProfileView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                                              HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(authService.login(request, httpServletRequest));
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileView> register(@RequestBody @Validated RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.okMessage("\u5df2\u9000\u51fa\u767b\u5f55");
    }
}
