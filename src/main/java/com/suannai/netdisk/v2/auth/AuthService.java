package com.suannai.netdisk.v2.auth;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.common.util.PasswordCodec;
import com.suannai.netdisk.common.util.RequestIpUtils;
import com.suannai.netdisk.common.util.SessionUserHelper;
import com.suannai.netdisk.service.AppSettingsService;
import com.suannai.netdisk.v2.profile.ProfileService;
import com.suannai.netdisk.v2.profile.UserProfileView;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AuthService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordCodec passwordCodec;
    private final AppSettingsService appSettingsService;
    private final WorkspaceService workspaceService;
    private final ProfileService profileService;

    private final RowMapper<UserCredential> credentialMapper = (resultSet, rowNum) -> new UserCredential(
            resultSet.getLong("id"),
            resultSet.getString("username"),
            resultSet.getString("password_hash"),
            resultSet.getString("password_algo"),
            resultSet.getBoolean("enabled")
    );

    public AuthService(NamedParameterJdbcTemplate jdbcTemplate,
                       PasswordCodec passwordCodec,
                       AppSettingsService appSettingsService,
                       WorkspaceService workspaceService,
                       ProfileService profileService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordCodec = passwordCodec;
        this.appSettingsService = appSettingsService;
        this.workspaceService = workspaceService;
        this.profileService = profileService;
    }

    @Transactional
    public UserProfileView login(LoginRequest request, HttpServletRequest httpServletRequest, HttpSession session) {
        if (!appSettingsService.isEnabled("allow_login")) {
            throw new ApiException("FORBIDDEN", "管理员已关闭登录");
        }

        UserCredential credential = findCredential(request.getUsername());
        if (credential == null || !credential.enabled()
                || !passwordCodec.matches(request.getPassword(), credential.passwordHash(), credential.passwordAlgo())) {
            throw new ApiException("INVALID_CREDENTIALS", "用户名或密码错误");
        }

        MapSqlParameterSource update = new MapSqlParameterSource()
                .addValue("id", credential.id())
                .addValue("ip", RequestIpUtils.clientIp(httpServletRequest));

        String sql = "UPDATE users SET last_login_at = NOW(), last_login_ip = :ip";
        if (!"BCRYPT".equalsIgnoreCase(credential.passwordAlgo())) {
            sql += ", password_hash = :passwordHash, password_algo = 'BCRYPT'";
            update.addValue("passwordHash", passwordCodec.encode(request.getPassword()));
        }
        sql += " WHERE id = :id";
        jdbcTemplate.update(sql, update);

        workspaceService.ensureRoot(credential.id());
        SessionUserHelper.signIn(session, credential.id(), credential.username());
        return profileService.toView(credential.id());
    }

    @Transactional
    public UserProfileView register(RegisterRequest request, HttpSession session) {
        if (!appSettingsService.isEnabled("allow_register")) {
            throw new ApiException("FORBIDDEN", "管理员已关闭注册");
        }

        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = :username",
                new MapSqlParameterSource("username", request.getUsername()),
                Long.class
        );
        if (existing != null && existing > 0) {
            throw new ApiException("USERNAME_EXISTS", "用户名已存在");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                "INSERT INTO users(username, password_hash, password_algo, nickname, enabled) " +
                        "VALUES(:username, :passwordHash, 'BCRYPT', :nickname, TRUE)",
                new MapSqlParameterSource()
                        .addValue("username", request.getUsername())
                        .addValue("passwordHash", passwordCodec.encode(request.getPassword()))
                        .addValue("nickname", request.getNickname()),
                keyHolder,
                new String[]{"id"}
        );

        Long id = Objects.requireNonNull(keyHolder.getKey(), "user id").longValue();
        workspaceService.ensureRoot(id);
        SessionUserHelper.signIn(session, id, request.getUsername());
        return profileService.toView(id);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    private UserCredential findCredential(String username) {
        List<UserCredential> users = jdbcTemplate.query(
                "SELECT id, username, password_hash, password_algo, enabled FROM users WHERE username = :username",
                new MapSqlParameterSource("username", username),
                credentialMapper
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private record UserCredential(Long id, String username, String passwordHash, String passwordAlgo, boolean enabled) {
    }
}
