package com.suannai.netdisk.v2.auth;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.common.util.PasswordCodec;
import com.suannai.netdisk.service.AppSettingsService;
import com.suannai.netdisk.v2.profile.ProfileService;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private AppSettingsService appSettingsService;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private ProfileService profileService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                jdbcTemplate,
                new PasswordCodec(),
                appSettingsService,
                workspaceService,
                profileService
        );
    }

    @Test
    void loginRejectsWhenLoginIsDisabled() {
        LoginRequest request = new LoginRequest();
        request.setUsername("demo");
        request.setPassword("secret123");

        when(appSettingsService.isEnabled("allow_login")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () ->
                authService.login(request, new MockHttpServletRequest(), new MockHttpSession())
        );
        assertEquals("FORBIDDEN", exception.getCode());
    }
}
