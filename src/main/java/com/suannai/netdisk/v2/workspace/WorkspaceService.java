package com.suannai.netdisk.v2.workspace;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.mapper.ServiceMapper;
import com.suannai.netdisk.mapper.SysFileTabMapper;
import com.suannai.netdisk.mapper.UserMapper;
import com.suannai.netdisk.model.Service;
import com.suannai.netdisk.model.ServiceExample;
import com.suannai.netdisk.model.SysFileTab;
import com.suannai.netdisk.model.User;
import com.suannai.netdisk.service.SysConfigService;
import com.suannai.netdisk.service.SysFileTabService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@org.springframework.stereotype.Service
public class WorkspaceService {
    private final ServiceMapper serviceMapper;
    private final SysFileTabMapper sysFileTabMapper;
    private final SysFileTabService sysFileTabService;
    private final SysConfigService sysConfigService;
    private final UserMapper userMapper;

    public WorkspaceService(ServiceMapper serviceMapper,
                            SysFileTabMapper sysFileTabMapper,
                            SysFileTabService sysFileTabService,
                            SysConfigService sysConfigService,
                            UserMapper userMapper) {
        this.serviceMapper = serviceMapper;
        this.sysFileTabMapper = sysFileTabMapper;
        this.sysFileTabService = sysFileTabService;
        this.sysConfigService = sysConfigService;
        this.userMapper = userMapper;
    }

    public DirectoryView loadDirectory(User user, Integer parentId) {
        Service current = getDirectoryOrRoot(user, parentId);
        List<Service> children = listChildren(user, current.getId());
        children.sort(Comparator
                .comparing(Service::getDirmask).reversed()
                .thenComparing(Service::getUserfilename, String.CASE_INSENSITIVE_ORDER));

        DirectoryView view = new DirectoryView();
        view.setCurrent(toView(current));
        view.setItems(children.stream().map(this::toView).collect(Collectors.toList()));
        view.setBreadcrumbs(buildBreadcrumbs(user, current));
        return view;
    }

    public FileEntryView getEntry(User user, int id) {
        return toView(requireOwnedService(user, id));
    }

    public Service getDirectoryOrRoot(User user, Integer parentId) {
        if (parentId == null) {
            return ensureRoot(user);
        }

        Service current = requireOwnedService(user, parentId);
        if (!Boolean.TRUE.equals(current.getDirmask())) {
            throw new ApiException("INVALID_DIRECTORY", "目标不是文件夹");
        }
        return current;
    }

    @Transactional
    public FileEntryView createDirectory(User user, Integer parentId, String name) {
        validateName(name);
        Service parent = getDirectoryOrRoot(user, parentId);
        if (findChild(user, parent.getId(), name) != null) {
            throw new ApiException("NAME_CONFLICT", "同目录下已存在同名文件或目录");
        }

        Service directory = new Service();
        directory.setUserid(user.getId());
        directory.setUserfilename(name);
        directory.setSysfilerecordid(-1);
        directory.setStatus(true);
        directory.setUploaddate(new Date());
        directory.setParentid(parent.getId());
        directory.setDirmask(true);
        serviceMapper.insertSelective(directory);

        return toView(findChild(user, parent.getId(), name));
    }

    @Transactional
    public int ensureDirectoryPath(User user, int parentId, String relativePath, List<DirectoryCreatedView> createdDirectories) {
        if (relativePath == null || relativePath.isBlank()) {
            return parentId;
        }

        int currentParentId = parentId;
        String[] segments = relativePath.split("/");
        StringBuilder currentPath = new StringBuilder();
        for (String rawSegment : segments) {
            String segment = rawSegment.trim();
            validateName(segment);
            Service child = findChild(user, currentParentId, segment);
            if (child == null) {
                Service directory = new Service();
                directory.setUserid(user.getId());
                directory.setUserfilename(segment);
                directory.setSysfilerecordid(-1);
                directory.setStatus(true);
                directory.setUploaddate(new Date());
                directory.setParentid(currentParentId);
                directory.setDirmask(true);
                serviceMapper.insertSelective(directory);
                child = findChild(user, currentParentId, segment);
                if (currentPath.length() > 0) {
                    currentPath.append('/');
                }
                currentPath.append(segment);
                createdDirectories.add(new DirectoryCreatedView(currentPath.toString(), child.getId()));
            } else if (!Boolean.TRUE.equals(child.getDirmask())) {
                throw new ApiException("NAME_CONFLICT", "目录路径被同名文件占用");
            } else {
                if (currentPath.length() > 0) {
                    currentPath.append('/');
                }
                currentPath.append(segment);
            }
            currentParentId = child.getId();
        }

        return currentParentId;
    }

    @Transactional
    public Service createFileReference(User user, int parentId, String fileName, int sysFileRecordId, boolean active) {
        validateName(fileName);
        if (findChild(user, parentId, fileName) != null) {
            throw new ApiException("NAME_CONFLICT", "同目录下已存在同名文件或目录");
        }

        Service service = new Service();
        service.setUserid(user.getId());
        service.setUserfilename(fileName);
        service.setSysfilerecordid(sysFileRecordId);
        service.setStatus(active);
        service.setUploaddate(new Date());
        service.setParentid(parentId);
        service.setDirmask(false);
        serviceMapper.insertSelective(service);

        return findChild(user, parentId, fileName);
    }

    public boolean existsSibling(User user, int parentId, String name) {
        return findChild(user, parentId, name) != null;
    }

    public String findAvailableName(User user, int parentId, String originalName) {
        if (!existsSibling(user, parentId, originalName)) {
            return originalName;
        }

        String name = originalName;
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            name = originalName.substring(0, dot);
            extension = originalName.substring(dot);
        }

        int index = 1;
        while (existsSibling(user, parentId, name + "-" + index + extension)) {
            index += 1;
        }
        return name + "-" + index + extension;
    }

    public Service requireOwnedService(User user, int serviceId) {
        Service service = serviceMapper.selectByPrimaryKey(serviceId);
        if (service == null || !Objects.equals(service.getUserid(), user.getId())) {
            throw new ApiException("NOT_FOUND", "文件或目录不存在");
        }
        return service;
    }

    @Transactional
    public void move(User user, List<Integer> ids, int targetParentId) {
        Service targetParent = getDirectoryOrRoot(user, targetParentId);
        List<Integer> uniqueIds = ids.stream().distinct().collect(Collectors.toList());
        for (Integer id : uniqueIds) {
            Service service = requireOwnedService(user, id);
            if (Objects.equals(service.getId(), targetParent.getId())) {
                throw new ApiException("INVALID_MOVE", "不能把目录移动到自己内部");
            }
            if (Objects.equals(service.getParentid(), targetParent.getId())) {
                continue;
            }
            if (Boolean.TRUE.equals(service.getDirmask()) && isAncestor(service.getId(), targetParent.getId(), user)) {
                throw new ApiException("INVALID_MOVE", "不能把目录移动到自己的子目录中");
            }
            if (existsSibling(user, targetParent.getId(), service.getUserfilename())) {
                throw new ApiException("NAME_CONFLICT", "目标目录存在同名条目");
            }

            service.setParentid(targetParent.getId());
            serviceMapper.updateByPrimaryKeySelective(service);
        }
    }

    @Transactional
    public void delete(User user, int id) {
        Service service = requireOwnedService(user, id);
        deleteRecursive(user, service);
        if (Objects.equals(user.getImgserviceid(), id)) {
            user.setImgserviceid(-1);
            userMapper.updateByPrimaryKeySelective(user);
        }
    }

    public void streamDownload(User user, int id, boolean inline, HttpServletResponse response) throws IOException {
        if (!sysConfigService.ConfigIsAllow("AllowDownload")) {
            throw new ApiException("FORBIDDEN", "管理员已关闭下载");
        }

        Service service = requireOwnedService(user, id);
        if (Boolean.TRUE.equals(service.getDirmask())) {
            String filename = URLEncoder.encode(service.getUserfilename() + ".zip", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + filename);
            response.setContentType("application/zip");
            try (ZipOutputStream outputStream = new ZipOutputStream(response.getOutputStream())) {
                zipDirectory(user, service, service.getUserfilename() + "/", outputStream);
            }
            return;
        }

        SysFileTab sysFileTab = sysFileTabMapper.selectByPrimaryKey(service.getSysfilerecordid());
        if (sysFileTab == null) {
            throw new ApiException("FILE_LOST", "文件记录不存在");
        }

        File file = new File(sysFileTab.getLocation());
        String disposition = inline ? "inline" : "attachment";
        response.setHeader(
                "Content-Disposition",
                disposition + ";filename=" + URLEncoder.encode(service.getUserfilename(), StandardCharsets.UTF_8)
        );
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(file.length());

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
             BufferedOutputStream outputStream = new BufferedOutputStream(response.getOutputStream())) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
        }
    }

    @Transactional
    public User bindAvatar(User user, int serviceId) {
        Service service = requireOwnedService(user, serviceId);
        user.setImgserviceid(service.getId());
        userMapper.updateByPrimaryKeySelective(user);
        return userMapper.selectByPrimaryKey(user.getId());
    }

    public Service ensureRoot(User user) {
        ServiceExample example = new ServiceExample();
        ServiceExample.Criteria criteria = example.createCriteria();
        criteria.andUseridEqualTo(user.getId());
        criteria.andParentidEqualTo(-1);
        List<Service> services = serviceMapper.selectByExample(example);
        if (!services.isEmpty()) {
            return services.get(0);
        }

        SysFileTab root = sysFileTabService.GetRoot();
        Service service = new Service();
        service.setUserid(user.getId());
        service.setUserfilename("/");
        service.setSysfilerecordid(root.getId());
        service.setStatus(true);
        service.setUploaddate(new Date());
        service.setParentid(-1);
        service.setDirmask(true);
        serviceMapper.insertSelective(service);

        services = serviceMapper.selectByExample(example);
        return services.get(0);
    }

    private List<Service> listChildren(User user, int parentId) {
        ServiceExample example = new ServiceExample();
        ServiceExample.Criteria criteria = example.createCriteria();
        criteria.andUseridEqualTo(user.getId());
        criteria.andParentidEqualTo(parentId);
        return serviceMapper.selectByExample(example);
    }

    private List<BreadcrumbView> buildBreadcrumbs(User user, Service current) {
        List<BreadcrumbView> breadcrumbs = new ArrayList<>();
        Service pointer = current;
        while (pointer != null) {
            breadcrumbs.add(0, new BreadcrumbView(pointer.getId(), pointer.getParentid() == -1 ? "/" : pointer.getUserfilename()));
            if (pointer.getParentid() == -1) {
                break;
            }
            pointer = requireOwnedService(user, pointer.getParentid());
        }
        return breadcrumbs;
    }

    private FileEntryView toView(Service service) {
        SysFileTab sysFileTab = service.getSysfilerecordid() != null && service.getSysfilerecordid() > 0
                ? sysFileTabMapper.selectByPrimaryKey(service.getSysfilerecordid())
                : null;

        FileEntryView view = new FileEntryView();
        view.setId(service.getId());
        view.setName(service.getParentid() != null && service.getParentid() == -1 ? "/" : service.getUserfilename());
        view.setDirectory(Boolean.TRUE.equals(service.getDirmask()));
        view.setStatus(Boolean.TRUE.equals(service.getStatus()));
        view.setParentId(service.getParentid());
        view.setUploadDate(service.getUploaddate() == null ? null : Instant.ofEpochMilli(service.getUploaddate().getTime()).toString());
        view.setSize(sysFileTab == null ? 0L : sysFileTab.getFilesize());
        view.setFileHash(sysFileTab == null ? null : sysFileTab.getFilehash());
        view.setDownloadUrl(Boolean.TRUE.equals(service.getDirmask()) ? null : "/api/v2/files/" + service.getId() + "/download");
        return view;
    }

    private Service findChild(User user, int parentId, String name) {
        ServiceExample example = new ServiceExample();
        ServiceExample.Criteria criteria = example.createCriteria();
        criteria.andUseridEqualTo(user.getId());
        criteria.andParentidEqualTo(parentId);
        criteria.andUserfilenameEqualTo(name);
        List<Service> services = serviceMapper.selectByExample(example);
        return services.isEmpty() ? null : services.get(0);
    }

    private boolean isAncestor(int candidateAncestorId, int nodeId, User user) {
        Service current = requireOwnedService(user, nodeId);
        while (current.getParentid() != null && current.getParentid() != -1) {
            if (Objects.equals(current.getParentid(), candidateAncestorId)) {
                return true;
            }
            current = requireOwnedService(user, current.getParentid());
        }
        return false;
    }

    private void deleteRecursive(User user, Service service) {
        if (Boolean.TRUE.equals(service.getDirmask())) {
            for (Service child : listChildren(user, service.getId())) {
                deleteRecursive(user, child);
            }
        }

        serviceMapper.deleteByPrimaryKey(service.getId());
        if (!Boolean.TRUE.equals(service.getDirmask()) && service.getSysfilerecordid() != null && service.getSysfilerecordid() > 0) {
            sysFileTabService.deleteIfUnUse(service.getSysfilerecordid());
        }
    }

    private void zipDirectory(User user, Service directory, String prefix, ZipOutputStream outputStream) throws IOException {
        List<Service> children = listChildren(user, directory.getId());
        if (children.isEmpty()) {
            outputStream.putNextEntry(new ZipEntry(prefix));
            outputStream.closeEntry();
            return;
        }

        for (Service child : children) {
            if (Boolean.TRUE.equals(child.getDirmask())) {
                zipDirectory(user, child, prefix + child.getUserfilename() + "/", outputStream);
                continue;
            }

            SysFileTab sysFileTab = sysFileTabMapper.selectByPrimaryKey(child.getSysfilerecordid());
            if (sysFileTab == null) {
                continue;
            }

            outputStream.putNextEntry(new ZipEntry(prefix + child.getUserfilename()));
            File file = new File(sysFileTab.getLocation());
            if (file.exists()) {
                try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                }
            }
            outputStream.closeEntry();
        }
    }

    public void deleteTempDirectory(File directory) {
        FileSystemUtils.deleteRecursively(directory);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException("INVALID_NAME", "名称不能为空");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new ApiException("INVALID_NAME", "名称包含非法字符");
        }
    }

    public static class DirectoryCreatedView {
        private final String path;
        private final Integer directoryId;

        public DirectoryCreatedView(String path, Integer directoryId) {
            this.path = path;
            this.directoryId = directoryId;
        }

        public String getPath() {
            return path;
        }

        public Integer getDirectoryId() {
            return directoryId;
        }
    }
}
