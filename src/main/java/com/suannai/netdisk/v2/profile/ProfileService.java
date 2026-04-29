package com.suannai.netdisk.v2.profile;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.common.util.SessionUser;
import com.suannai.netdisk.v2.uploads.UploadService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import java.util.List;

@Service
public class ProfileService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UploadService uploadService;

    private final RowMapper<UserRow> userMapper = (resultSet, rowNum) -> new UserRow(
            resultSet.getLong("id"),
            resultSet.getString("username"),
            resultSet.getString("nickname"),
            resultSet.getBoolean("enabled"),
            resultSet.getObject("avatar_entry_id", Long.class)
    );

    public ProfileService(NamedParameterJdbcTemplate jdbcTemplate, UploadService uploadService) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadService = uploadService;
    }

    public UserProfileView toView(SessionUser user) {
        return toView(user.id());
    }

    public UserProfileView toView(Long userId) {
        UserRow user = requireUser(userId);
        UserProfileView view = new UserProfileView();
        view.setId(user.id());
        view.setUsername(user.username());
        view.setNickname(user.nickname());
        view.setStatus(user.enabled());
        view.setImgServiceId(user.avatarEntryId());
        view.setAvatarUrl(user.avatarEntryId() == null ? null : "/api/v2/files/" + user.avatarEntryId() + "/download?inline=true");
        return view;
    }

    @Transactional
    public UserProfileView updateProfile(SessionUser user, String nickname, HttpSession session) {
        jdbcTemplate.update(
                "UPDATE users SET nickname = :nickname WHERE id = :id",
                new MapSqlParameterSource().addValue("nickname", nickname).addValue("id", user.id())
        );
        return toView(user.id());
    }

    @Transactional
    public UserProfileView updateAvatar(SessionUser user, MultipartFile file, HttpSession session) throws Exception {
        if (file.isEmpty()) {
            throw new ApiException("EMPTY_FILE", "请选择头像文件");
        }

        Long avatarEntryId = uploadService.storeAvatar(user, file);
        jdbcTemplate.update(
                "UPDATE users SET avatar_entry_id = :avatarEntryId WHERE id = :id",
                new MapSqlParameterSource().addValue("avatarEntryId", avatarEntryId).addValue("id", user.id())
        );
        return toView(user.id());
    }

    private UserRow requireUser(Long userId) {
        List<UserRow> users = jdbcTemplate.query(
                "SELECT id, username, nickname, enabled, avatar_entry_id FROM users WHERE id = :id",
                new MapSqlParameterSource("id", userId),
                userMapper
        );
        if (users.isEmpty()) {
            throw new ApiException("NOT_FOUND", "用户不存在");
        }
        return users.get(0);
    }

    private record UserRow(Long id, String username, String nickname, boolean enabled, Long avatarEntryId) {
    }
}
