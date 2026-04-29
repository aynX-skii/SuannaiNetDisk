package com.suannai.netdisk.v2.auth;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.common.util.PasswordCodec;
import com.suannai.netdisk.mapper.UserMapper;
import com.suannai.netdisk.model.User;
import com.suannai.netdisk.service.SysConfigService;
import com.suannai.netdisk.utils.IPUtils;
import com.suannai.netdisk.v2.profile.ProfileService;
import com.suannai.netdisk.v2.profile.UserProfileView;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Service
public class AuthService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordCodec passwordCodec;
    private final UserMapper userMapper;
    private final SysConfigService sysConfigService;
    private final WorkspaceService workspaceService;
    private final ProfileService profileService;

    public AuthService(NamedParameterJdbcTemplate jdbcTemplate,
                       PasswordCodec passwordCodec,
                       UserMapper userMapper,
                       SysConfigService sysConfigService,
                       WorkspaceService workspaceService,
                       ProfileService profileService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordCodec = passwordCodec;
        this.userMapper = userMapper;
        this.sysConfigService = sysConfigService;
        this.workspaceService = workspaceService;
        this.profileService = profileService;
    }

    @Transactional
    public UserProfileView login(LoginRequest request, HttpServletRequest httpServletRequest, HttpSession session) {
        if (!sysConfigService.ConfigIsAllow("AllowLogin")) {
            throw new ApiException("FORBIDDEN", "管理员已关闭登录");
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT ID, Password, Status, COALESCE(PasswordAlgo, 'MD5') AS PasswordAlgo FROM users WHERE UserName = :username",
                new MapSqlParameterSource("username", request.getUsername())
        );
        if (users.isEmpty()) {
            throw new ApiException("INVALID_CREDENTIALS", "用户名或密码错误");
        }

        Map<String, Object> row = users.get(0);
        Integer id = ((Number) row.get("ID")).intValue();
        String password = (String) row.get("Password");
        String algorithm = (String) row.get("PasswordAlgo");
        boolean enabled = toBoolean(row.get("Status"));

        if (!enabled || !passwordCodec.matches(request.getPassword(), password, algorithm)) {
            throw new ApiException("INVALID_CREDENTIALS", "用户名或密码错误");
        }

        String encodedPassword = password;
        String encodedAlgo = algorithm;
        if (!"BCRYPT".equalsIgnoreCase(algorithm)) {
            encodedPassword = passwordCodec.encode(request.getPassword());
            encodedAlgo = "BCRYPT";
        }

        jdbcTemplate.update(
                "UPDATE users SET Password = :password, PasswordAlgo = :algo, LastLoginTime = NOW(), LastLoginIP = :ip WHERE ID = :id",
                new MapSqlParameterSource()
                        .addValue("password", encodedPassword)
                        .addValue("algo", encodedAlgo)
                        .addValue("ip", IPUtils.getIpAddr(httpServletRequest))
                        .addValue("id", id)
        );

        User user = userMapper.selectByPrimaryKey(id);
        workspaceService.ensureRoot(user);
        session.setAttribute("user", user);
        session.setAttribute("CurWorkDir", "/");
        session.setAttribute("curService", null);
        return profileService.toView(user);
    }

    @Transactional
    public UserProfileView register(RegisterRequest request, HttpSession session) {
        if (!sysConfigService.ConfigIsAllow("AllowRegister")) {
            throw new ApiException("FORBIDDEN", "管理员已关闭注册");
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE UserName = :username",
                new MapSqlParameterSource("username", request.getUsername()),
                Integer.class
        );
        if (count != null && count > 0) {
            throw new ApiException("USERNAME_EXISTS", "用户名已存在");
        }

        jdbcTemplate.update(
                "INSERT INTO users(UserName, Password, PasswordAlgo, Status, ImgServiceID, NickName) " +
                        "VALUES(:username, :password, 'BCRYPT', 1, -1, :nickname)",
                new MapSqlParameterSource()
                        .addValue("username", request.getUsername())
                        .addValue("password", passwordCodec.encode(request.getPassword()))
                        .addValue("nickname", request.getNickname())
        );

        Integer id = jdbcTemplate.queryForObject(
                "SELECT ID FROM users WHERE UserName = :username",
                new MapSqlParameterSource("username", request.getUsername()),
                Integer.class
        );

        User user = userMapper.selectByPrimaryKey(id);
        workspaceService.ensureRoot(user);
        session.setAttribute("user", user);
        session.setAttribute("CurWorkDir", "/");
        session.setAttribute("curService", null);
        return profileService.toView(user);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            return bytes.length > 0 && bytes[0] != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
