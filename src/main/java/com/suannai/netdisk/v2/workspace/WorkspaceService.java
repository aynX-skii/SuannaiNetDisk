package com.suannai.netdisk.v2.workspace;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.common.util.SessionUser;
import com.suannai.netdisk.service.AppSettingsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class WorkspaceService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AppSettingsService appSettingsService;

    private final RowMapper<EntryRecord> entryMapper = (resultSet, rowNum) -> new EntryRecord(
            resultSet.getLong("id"),
            resultSet.getLong("user_id"),
            resultSet.getObject("parent_id", Long.class),
            resultSet.getObject("storage_object_id", Long.class),
            resultSet.getString("name"),
            resultSet.getString("entry_type"),
            "ACTIVE".equals(resultSet.getString("status")),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at")),
            resultSet.getString("sha256"),
            resultSet.getString("md5"),
            resultSet.getObject("file_size", Long.class),
            resultSet.getString("storage_path")
    );

    public WorkspaceService(NamedParameterJdbcTemplate jdbcTemplate, AppSettingsService appSettingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.appSettingsService = appSettingsService;
    }

    public DirectoryView loadDirectory(SessionUser user, Long parentId) {
        EntryRecord current = getDirectoryOrRoot(user.id(), parentId);
        List<FileEntryView> children = listChildren(current.id()).stream().map(this::toView).collect(Collectors.toList());

        DirectoryView view = new DirectoryView();
        view.setCurrent(toView(current));
        view.setItems(children);
        view.setBreadcrumbs(buildBreadcrumbs(user.id(), current));
        return view;
    }

    public FileEntryView getEntry(SessionUser user, Long id) {
        return toView(requireOwnedEntry(user.id(), id));
    }

    public EntryRecord getDirectoryOrRoot(Long userId, Long parentId) {
        if (parentId == null) {
            return ensureRoot(userId);
        }

        EntryRecord current = requireOwnedEntry(userId, parentId);
        if (!current.directory()) {
            throw new ApiException("INVALID_DIRECTORY", "目标不是文件夹");
        }
        return current;
    }

    @Transactional
    public FileEntryView createDirectory(SessionUser user, Long parentId, String name) {
        EntryRecord parent = getDirectoryOrRoot(user.id(), parentId);
        return toView(createDirectoryInternal(user.id(), parent.id(), name));
    }

    @Transactional
    public Long ensureDirectoryPath(SessionUser user, Long parentId, String relativePath, List<DirectoryCreatedView> createdDirectories) {
        if (relativePath == null || relativePath.isBlank()) {
            return parentId;
        }

        Long currentParentId = parentId;
        StringBuilder currentPath = new StringBuilder();
        for (String rawSegment : relativePath.split("/")) {
            String segment = rawSegment.trim();
            validateName(segment);
            EntryRecord child = findChild(user.id(), currentParentId, segment);
            if (child == null) {
                child = createDirectoryInternal(user.id(), currentParentId, segment);
                if (!currentPath.isEmpty()) {
                    currentPath.append('/');
                }
                currentPath.append(segment);
                createdDirectories.add(new DirectoryCreatedView(currentPath.toString(), child.id()));
            } else if (!child.directory()) {
                throw new ApiException("NAME_CONFLICT", "目录路径被同名文件占用");
            } else {
                if (!currentPath.isEmpty()) {
                    currentPath.append('/');
                }
                currentPath.append(segment);
            }
            currentParentId = child.id();
        }
        return currentParentId;
    }

    @Transactional
    public EntryRecord createFileReference(SessionUser user, Long parentId, String fileName, Long storageObjectId) {
        validateName(fileName);
        if (findChild(user.id(), parentId, fileName) != null) {
            throw new ApiException("NAME_CONFLICT", "同目录下已存在同名文件或目录");
        }

        Long id = insertEntry(user.id(), parentId, storageObjectId, fileName, "FILE");
        jdbcTemplate.update(
                "UPDATE storage_objects SET ref_count = ref_count + 1 WHERE id = :id",
                new MapSqlParameterSource("id", storageObjectId)
        );
        return requireOwnedEntry(user.id(), id);
    }

    public boolean existsSibling(SessionUser user, Long parentId, String name) {
        return findChild(user.id(), parentId, name) != null;
    }

    public String findAvailableName(SessionUser user, Long parentId, String originalName) {
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

    @Transactional
    public void move(SessionUser user, List<Long> ids, Long targetParentId) {
        EntryRecord targetParent = getDirectoryOrRoot(user.id(), targetParentId);
        for (Long id : ids.stream().distinct().toList()) {
            EntryRecord entry = requireOwnedEntry(user.id(), id);
            if (Objects.equals(entry.id(), targetParent.id())) {
                throw new ApiException("INVALID_MOVE", "不能把目录移动到自己内部");
            }
            if (Objects.equals(entry.parentId(), targetParent.id())) {
                continue;
            }
            if (entry.directory() && isAncestor(user.id(), entry.id(), targetParent.id())) {
                throw new ApiException("INVALID_MOVE", "不能把目录移动到自己的子目录中");
            }
            if (findChild(user.id(), targetParent.id(), entry.name()) != null) {
                throw new ApiException("NAME_CONFLICT", "目标目录存在同名条目");
            }

            jdbcTemplate.update(
                    "UPDATE file_entries SET parent_id = :parentId WHERE id = :id AND user_id = :userId",
                    params(user.id()).addValue("id", entry.id()).addValue("parentId", targetParent.id())
            );
        }
    }

    @Transactional
    public void delete(SessionUser user, Long id) {
        EntryRecord entry = requireOwnedEntry(user.id(), id);
        jdbcTemplate.update(
                "UPDATE users SET avatar_entry_id = NULL WHERE id = :userId AND avatar_entry_id = :entryId",
                params(user.id()).addValue("entryId", id)
        );
        deleteRecursive(user.id(), entry);
    }

    public void streamDownload(SessionUser user, Long id, boolean inline, HttpServletResponse response) throws IOException {
        if (!appSettingsService.isEnabled("allow_download")) {
            throw new ApiException("FORBIDDEN", "管理员已关闭下载");
        }

        EntryRecord entry = requireOwnedEntry(user.id(), id);
        if (entry.directory()) {
            String filename = URLEncoder.encode(entry.name() + ".zip", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + filename);
            response.setContentType("application/zip");
            try (ZipOutputStream outputStream = new ZipOutputStream(response.getOutputStream())) {
                zipDirectory(user.id(), entry, entry.name() + "/", outputStream);
            }
            return;
        }

        if (entry.storagePath() == null) {
            throw new ApiException("FILE_LOST", "文件记录不存在");
        }

        Path path = Path.of(entry.storagePath());
        if (!Files.exists(path)) {
            throw new ApiException("FILE_LOST", "文件已丢失");
        }

        String disposition = inline ? "inline" : "attachment";
        response.setHeader("Content-Disposition", disposition + ";filename=" + URLEncoder.encode(entry.name(), StandardCharsets.UTF_8));
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(Files.size(path));

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(path.toFile()));
             BufferedOutputStream outputStream = new BufferedOutputStream(response.getOutputStream())) {
            inputStream.transferTo(outputStream);
            outputStream.flush();
        }
    }

    public EntryRecord ensureRoot(Long userId) {
        List<EntryRecord> roots = jdbcTemplate.query(
                baseEntrySql() + " WHERE e.user_id = :userId AND e.parent_id IS NULL AND e.status = 'ACTIVE'",
                params(userId),
                entryMapper
        );
        if (!roots.isEmpty()) {
            return roots.get(0);
        }

        Long id = insertEntry(userId, null, null, "/", "DIRECTORY");
        return requireOwnedEntry(userId, id);
    }

    public EntryRecord requireOwnedEntry(Long userId, Long id) {
        List<EntryRecord> entries = jdbcTemplate.query(
                baseEntrySql() + " WHERE e.id = :id AND e.user_id = :userId AND e.status = 'ACTIVE'",
                params(userId).addValue("id", id),
                entryMapper
        );
        if (entries.isEmpty()) {
            throw new ApiException("NOT_FOUND", "文件或目录不存在");
        }
        return entries.get(0);
    }

    private EntryRecord createDirectoryInternal(Long userId, Long parentId, String name) {
        validateName(name);
        if (findChild(userId, parentId, name) != null) {
            throw new ApiException("NAME_CONFLICT", "同目录下已存在同名文件或目录");
        }

        Long id = insertEntry(userId, parentId, null, name, "DIRECTORY");
        return requireOwnedEntry(userId, id);
    }

    private Long insertEntry(Long userId, Long parentId, Long storageObjectId, String name, String entryType) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                "INSERT INTO file_entries(user_id, parent_id, storage_object_id, name, entry_type, status) " +
                        "VALUES(:userId, :parentId, :storageObjectId, :name, :entryType, 'ACTIVE')",
                params(userId)
                        .addValue("parentId", parentId)
                        .addValue("storageObjectId", storageObjectId)
                        .addValue("name", name)
                        .addValue("entryType", entryType),
                keyHolder,
                new String[]{"id"}
        );
        return keyHolder.getKey().longValue();
    }

    private List<EntryRecord> listChildren(Long parentId) {
        return jdbcTemplate.query(
                baseEntrySql() + " WHERE e.parent_id = :parentId AND e.status = 'ACTIVE' " +
                        "ORDER BY e.entry_type = 'DIRECTORY' DESC, e.name ASC",
                new MapSqlParameterSource("parentId", parentId),
                entryMapper
        );
    }

    private List<BreadcrumbView> buildBreadcrumbs(Long userId, EntryRecord current) {
        List<BreadcrumbView> breadcrumbs = new ArrayList<>();
        EntryRecord pointer = current;
        while (pointer != null) {
            breadcrumbs.add(0, new BreadcrumbView(pointer.id(), pointer.parentId() == null ? "/" : pointer.name()));
            if (pointer.parentId() == null) {
                break;
            }
            pointer = requireOwnedEntry(userId, pointer.parentId());
        }
        return breadcrumbs;
    }

    private FileEntryView toView(EntryRecord entry) {
        FileEntryView view = new FileEntryView();
        view.setId(entry.id());
        view.setName(entry.parentId() == null ? "/" : entry.name());
        view.setDirectory(entry.directory());
        view.setStatus(entry.active());
        view.setParentId(entry.parentId());
        view.setUploadDate(entry.createdAt() == null ? null : entry.createdAt().toString());
        view.setSize(entry.fileSize() == null ? 0L : entry.fileSize());
        view.setFileHash(entry.sha256());
        view.setDownloadUrl(entry.directory() ? null : "/api/v2/files/" + entry.id() + "/download");
        return view;
    }

    private EntryRecord findChild(Long userId, Long parentId, String name) {
        List<EntryRecord> entries = jdbcTemplate.query(
                baseEntrySql() + " WHERE e.user_id = :userId AND e.parent_key = COALESCE(:parentId, 0) " +
                        "AND e.name = :name AND e.status = 'ACTIVE'",
                params(userId).addValue("parentId", parentId).addValue("name", name),
                entryMapper
        );
        return entries.isEmpty() ? null : entries.get(0);
    }

    private boolean isAncestor(Long userId, Long candidateAncestorId, Long nodeId) {
        EntryRecord current = requireOwnedEntry(userId, nodeId);
        while (current.parentId() != null) {
            if (Objects.equals(current.parentId(), candidateAncestorId)) {
                return true;
            }
            current = requireOwnedEntry(userId, current.parentId());
        }
        return false;
    }

    private void deleteRecursive(Long userId, EntryRecord entry) {
        if (entry.directory()) {
            for (EntryRecord child : listChildren(entry.id())) {
                deleteRecursive(userId, child);
            }
        }

        jdbcTemplate.update("DELETE FROM file_entries WHERE id = :id AND user_id = :userId", params(userId).addValue("id", entry.id()));
        if (!entry.directory() && entry.storageObjectId() != null) {
            releaseStorageObject(entry.storageObjectId(), entry.storagePath());
        }
    }

    private void releaseStorageObject(Long storageObjectId, String storagePath) {
        jdbcTemplate.update(
                "UPDATE storage_objects SET ref_count = CASE WHEN ref_count > 0 THEN ref_count - 1 ELSE 0 END WHERE id = :id",
                new MapSqlParameterSource("id", storageObjectId)
        );

        if (!appSettingsService.isEnabled("allow_delete_storage_object")) {
            return;
        }

        Integer references = jdbcTemplate.queryForObject(
                "SELECT ref_count FROM storage_objects WHERE id = :id",
                new MapSqlParameterSource("id", storageObjectId),
                Integer.class
        );
        if (references != null && references == 0) {
            if (storagePath != null) {
                FileSystemUtils.deleteRecursively(new File(storagePath));
            }
            jdbcTemplate.update("DELETE FROM storage_objects WHERE id = :id", new MapSqlParameterSource("id", storageObjectId));
        }
    }

    private void zipDirectory(Long userId, EntryRecord directory, String prefix, ZipOutputStream outputStream) throws IOException {
        List<EntryRecord> children = listChildren(directory.id());
        if (children.isEmpty()) {
            outputStream.putNextEntry(new ZipEntry(prefix));
            outputStream.closeEntry();
            return;
        }

        for (EntryRecord child : children) {
            if (child.directory()) {
                zipDirectory(userId, child, prefix + child.name() + "/", outputStream);
                continue;
            }

            outputStream.putNextEntry(new ZipEntry(prefix + child.name()));
            if (child.storagePath() != null) {
                File file = new File(child.storagePath());
                if (file.exists()) {
                    try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
                        inputStream.transferTo(outputStream);
                    }
                }
            }
            outputStream.closeEntry();
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException("INVALID_NAME", "名称不能为空");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new ApiException("INVALID_NAME", "名称包含非法字符");
        }
    }

    private String baseEntrySql() {
        return "SELECT e.id, e.user_id, e.parent_id, e.storage_object_id, e.name, e.entry_type, e.status, " +
                "e.created_at, e.updated_at, o.sha256, o.md5, o.file_size, o.storage_path " +
                "FROM file_entries e LEFT JOIN storage_objects o ON e.storage_object_id = o.id";
    }

    private MapSqlParameterSource params(Long userId) {
        return new MapSqlParameterSource("userId", userId);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record EntryRecord(Long id,
                              Long userId,
                              Long parentId,
                              Long storageObjectId,
                              String name,
                              String entryType,
                              boolean active,
                              Instant createdAt,
                              Instant updatedAt,
                              String sha256,
                              String md5,
                              Long fileSize,
                              String storagePath) {
        public boolean directory() {
            return "DIRECTORY".equals(entryType);
        }
    }

    public record DirectoryCreatedView(String path, Long directoryId) {
    }
}
