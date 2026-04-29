package com.suannai.netdisk.v2.profile;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.mapper.UserMapper;
import com.suannai.netdisk.model.Service;
import com.suannai.netdisk.model.User;
import com.suannai.netdisk.v2.uploads.UploadService;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;

@org.springframework.stereotype.Service
public class ProfileService {
    private final UserMapper userMapper;
    private final WorkspaceService workspaceService;
    private final UploadService uploadService;

    public ProfileService(UserMapper userMapper, WorkspaceService workspaceService, UploadService uploadService) {
        this.userMapper = userMapper;
        this.workspaceService = workspaceService;
        this.uploadService = uploadService;
    }

    public UserProfileView toView(User user) {
        User freshUser = userMapper.selectByPrimaryKey(user.getId());
        if (freshUser == null) {
            throw new ApiException("NOT_FOUND", "用户不存在");
        }

        UserProfileView view = new UserProfileView();
        view.setId(freshUser.getId());
        view.setUsername(freshUser.getUsername());
        view.setNickname(freshUser.getNickname());
        view.setStatus(Boolean.TRUE.equals(freshUser.getStatus()));
        view.setImgServiceId(freshUser.getImgserviceid());
        view.setAvatarUrl(
                freshUser.getImgserviceid() != null && freshUser.getImgserviceid() > 0
                        ? "/api/v2/files/" + freshUser.getImgserviceid() + "/download?inline=true"
                        : null
        );
        return view;
    }

    @Transactional
    public UserProfileView updateProfile(User user, String nickname, HttpSession session) {
        user.setNickname(nickname);
        userMapper.updateByPrimaryKeySelective(user);
        User fresh = userMapper.selectByPrimaryKey(user.getId());
        session.setAttribute("user", fresh);
        return toView(fresh);
    }

    @Transactional
    public UserProfileView updateAvatar(User user, MultipartFile file, HttpSession session) throws Exception {
        if (file.isEmpty()) {
            throw new ApiException("EMPTY_FILE", "请选择头像文件");
        }
        Service avatarService = uploadService.storeAvatar(user, file);
        User updated = workspaceService.bindAvatar(user, avatarService.getId());
        session.setAttribute("user", updated);
        return toView(updated);
    }
}
