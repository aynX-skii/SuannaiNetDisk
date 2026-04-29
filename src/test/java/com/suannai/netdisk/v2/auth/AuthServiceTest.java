package com.suannai.netdisk.v2.auth;

import com.suannai.netdisk.common.util.PasswordCodec;
import com.suannai.netdisk.mapper.UserMapper;
import com.suannai.netdisk.model.User;
import com.suannai.netdisk.service.SysConfigService;
import com.suannai.netdisk.v2.profile.ProfileService;
import com.suannai.netdisk.v2.profile.UserProfileView;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private UserMapper userMapper;
    @Mock
    private SysConfigService sysConfigService;
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
                userMapper,
                sysConfigService,
                workspaceService,
                profileService
        );
    }

    @Test
    void loginUpgradesLegacyMd5Password() {
        LoginRequest request = new LoginRequest();
        request.setUsername("demo");
        request.setPassword("secret123");

        User user = new User();
        user.setId(1);
        user.setUsername("demo");
        user.setNickname("Demo");
        user.setStatus(true);

        UserProfileView view = new UserProfileView();
        view.setId(1);
        view.setUsername("demo");
        view.setNickname("Demo");
        view.setStatus(true);

        when(sysConfigService.ConfigIsAllow("AllowLogin")).thenReturn(true);
        when(jdbcTemplate.queryForList(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "ID", 1,
                        "Password", DigestUtils.md5Hex("secret123"),
                        "Status", true,
                        "PasswordAlgo", "MD5"
                )));
        when(userMapper.selectByPrimaryKey(1)).thenReturn(user);
        when(profileService.toView(user)).thenReturn(view);

        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setRemoteAddr("127.0.0.1");
        MockHttpSession session = new MockHttpSession();

        authService.login(request, httpServletRequest, session);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(eq("UPDATE users SET Password = :password, PasswordAlgo = :algo, LastLoginTime = NOW(), LastLoginIP = :ip WHERE ID = :id"), captor.capture());
        MapSqlParameterSource sqlParameterSource = captor.getValue();
        assertEquals("BCRYPT", sqlParameterSource.getValue("algo"));
        assertNotEquals(DigestUtils.md5Hex("secret123"), sqlParameterSource.getValue("password"));
        assertEquals(user, session.getAttribute("user"));
        verify(workspaceService).ensureRoot(user);
    }
}
