package com.suannai.netdisk.v2.uploads;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.common.util.FileHashUtils;
import com.suannai.netdisk.config.StorageProperties;
import com.suannai.netdisk.mapper.SysFileTabMapper;
import com.suannai.netdisk.mapper.UserMapper;
import com.suannai.netdisk.model.Service;
import com.suannai.netdisk.model.SysFileTab;
import com.suannai.netdisk.model.User;
import com.suannai.netdisk.service.SysConfigService;
import com.suannai.netdisk.service.SysFileTabService;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class UploadService {
    private static final int DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final WorkspaceService workspaceService;
    private final SysFileTabService sysFileTabService;
    private final SysFileTabMapper sysFileTabMapper;
    private final UserMapper userMapper;
    private final StorageProperties storageProperties;
    private final SysConfigService sysConfigService;

    private final RowMapper<UploadSessionRecord> uploadSessionMapper = (resultSet, rowNum) -> {
        UploadSessionRecord record = new UploadSessionRecord();
        record.uploadId = resultSet.getString("UploadId");
        record.userId = resultSet.getInt("UserID");
        record.clientId = resultSet.getString("ClientId");
        record.parentId = resultSet.getInt("ParentID");
        record.relativePath = resultSet.getString("RelativePath");
        record.fileName = resultSet.getString("FileName");
        record.fileSize = resultSet.getLong("FileSize");
        record.chunkSize = resultSet.getInt("ChunkSize");
        record.totalParts = resultSet.getInt("TotalParts");
        record.fileHashHint = resultSet.getString("FileHashHint");
        record.status = resultSet.getString("Status");
        record.tempDir = resultSet.getString("TempDir");
        record.expiresAt = resultSet.getTimestamp("ExpiresAt").toLocalDateTime();
        return record;
    };

    public UploadService(NamedParameterJdbcTemplate jdbcTemplate,
                         WorkspaceService workspaceService,
                         SysFileTabService sysFileTabService,
                         SysFileTabMapper sysFileTabMapper,
                         UserMapper userMapper,
                         StorageProperties storageProperties,
                         SysConfigService sysConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.workspaceService = workspaceService;
        this.sysFileTabService = sysFileTabService;
        this.sysFileTabMapper = sysFileTabMapper;
        this.userMapper = userMapper;
        this.storageProperties = storageProperties;
        this.sysConfigService = sysConfigService;
    }

    @Transactional
    public PrepareUploadResponse prepare(User user, PrepareUploadRequest request) {
        if (!sysConfigService.ConfigIsAllow("AllowUpload")) {
            throw new ApiException("FORBIDDEN", "管理员已关闭上传");
        }

        Service parent = workspaceService.getDirectoryOrRoot(user, request.getParentId());
        PrepareUploadResponse response = new PrepareUploadResponse();
        List<WorkspaceService.DirectoryCreatedView> createdDirectories = new ArrayList<>();

        List<PrepareUploadDirectoryRequest> directoryRequests = request.getDirectories() == null
                ? List.of()
                : request.getDirectories();
        directoryRequests.stream()
                .sorted(Comparator.comparingInt(entry -> normalizeRelativePath(entry.getPath()).split("/").length))
                .forEach(entry -> workspaceService.ensureDirectoryPath(
                        user,
                        parent.getId(),
                        normalizeRelativePath(entry.getPath()),
                        createdDirectories
                ));

        List<PrepareUploadFileRequest> fileRequests = request.getFiles() == null ? List.of() : request.getFiles();
        for (PrepareUploadFileRequest file : fileRequests) {
            String relativePath = normalizeRelativePath(file.getRelativePath());
            int targetParentId = workspaceService.ensureDirectoryPath(user, parent.getId(), relativePath, createdDirectories);

            if (workspaceService.existsSibling(user, targetParentId, file.getName())) {
                response.getConflicts().add(new PrepareUploadResponse.ConflictPayload(
                        file.getClientId(),
                        relativePath.isBlank() ? file.getName() : relativePath + "/" + file.getName(),
                        "同目录已存在同名文件或目录"
                ));
                continue;
            }

            if (file.getMd5() != null && !file.getMd5().isBlank()) {
                SysFileTab sysFileTab = sysFileTabService.findByHash(file.getMd5());
                if (sysFileTab != null) {
                    Service service = workspaceService.createFileReference(user, targetParentId, file.getName(), sysFileTab.getId(), true);
                    response.getInstantFiles().add(new PrepareUploadResponse.InstantFilePayload(file.getClientId(), service.getId(), "秒传成功"));
                    continue;
                }
            }

            UploadSessionRecord existingSession = findSessionByClientId(user.getId(), file.getClientId());
            if (existingSession != null && !isExpired(existingSession) && !"COMPLETED".equals(existingSession.status) && !"CANCELED".equals(existingSession.status)) {
                response.getUploadSessions().add(toSessionView(existingSession, loadUploadedParts(existingSession.uploadId)));
                continue;
            }
            if (existingSession != null) {
                cleanupSession(existingSession);
            }

            String uploadId = UUID.randomUUID().toString().replace("-", "");
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(storageProperties.getUploadSessionTtlHours());
            int totalParts = file.getSize() == 0 ? 0 : (int) Math.ceil((double) file.getSize() / DEFAULT_CHUNK_SIZE);

            jdbcTemplate.update(
                    "INSERT INTO upload_session(UploadId, UserID, ClientId, ParentID, RelativePath, FileName, FileSize, ChunkSize, TotalParts, FileHashHint, Status, TempDir, ExpiresAt) " +
                            "VALUES(:uploadId, :userId, :clientId, :parentId, :relativePath, :fileName, :fileSize, :chunkSize, :totalParts, :fileHashHint, 'PENDING', :tempDir, :expiresAt)",
                    new MapSqlParameterSource()
                            .addValue("uploadId", uploadId)
                            .addValue("userId", user.getId())
                            .addValue("clientId", file.getClientId())
                            .addValue("parentId", targetParentId)
                            .addValue("relativePath", relativePath)
                            .addValue("fileName", file.getName())
                            .addValue("fileSize", file.getSize())
                            .addValue("chunkSize", DEFAULT_CHUNK_SIZE)
                            .addValue("totalParts", totalParts)
                            .addValue("fileHashHint", file.getMd5())
                            .addValue("tempDir", resolveTempDir(uploadId).toString())
                            .addValue("expiresAt", java.sql.Timestamp.valueOf(expiresAt))
            );

            response.getUploadSessions().add(toSessionView(findSession(user.getId(), uploadId), List.of()));
        }

        response.setCreatedDirectories(createdDirectories.stream()
                .map(entry -> new PrepareUploadResponse.CreatedDirectoryPayload(entry.getPath(), entry.getDirectoryId()))
                .collect(Collectors.toList()));
        return response;
    }

    @Transactional
    public void storePart(User user, String uploadId, Integer partNumber, byte[] body) throws IOException {
        UploadSessionRecord session = requireActiveSession(user, uploadId);
        if (partNumber < 1 || partNumber > Math.max(1, session.totalParts)) {
            throw new ApiException("INVALID_PART", "分片序号超出范围");
        }

        Path tempDir = Path.of(session.tempDir);
        Files.createDirectories(tempDir);
        Path partPath = tempDir.resolve(partNumber + ".part");
        if (Files.exists(partPath)) {
            Long uploadedSize = jdbcTemplate.query(
                    "SELECT PartSize FROM upload_part WHERE UploadId = :uploadId AND PartNumber = :partNumber",
                    new MapSqlParameterSource().addValue("uploadId", uploadId).addValue("partNumber", partNumber),
                    resultSet -> resultSet.next() ? resultSet.getLong(1) : null
            );
            if (uploadedSize != null && uploadedSize == body.length) {
                return;
            }
        }

        Files.write(partPath, body);
        jdbcTemplate.update(
                "INSERT INTO upload_part(UploadId, PartNumber, PartSize) VALUES(:uploadId, :partNumber, :partSize) " +
                        "ON DUPLICATE KEY UPDATE PartSize = VALUES(PartSize)",
                new MapSqlParameterSource()
                        .addValue("uploadId", uploadId)
                        .addValue("partNumber", partNumber)
                        .addValue("partSize", body.length)
        );
        touchSession(uploadId, "UPLOADING");
    }

    @Transactional
    public void complete(User user, String uploadId) throws Exception {
        UploadSessionRecord session = requireActiveSession(user, uploadId);

        Path tempDir = Path.of(session.tempDir);
        Files.createDirectories(tempDir);
        Path assembled = tempDir.resolve("assembled.bin");

        if (session.totalParts == 0) {
            Files.write(assembled, new byte[0]);
        } else {
            List<Integer> uploadedParts = loadUploadedParts(uploadId);
            if (uploadedParts.size() != session.totalParts) {
                throw new ApiException("MISSING_PARTS", "仍有分片未上传");
            }
            mergeParts(tempDir, uploadedParts, assembled);
        }

        String fileHash = FileHashUtils.md5Hex(new BufferedInputStream(new FileInputStream(assembled.toFile())));
        SysFileTab sysFileTab = sysFileTabService.findByHash(fileHash);
        if (sysFileTab == null) {
            Path finalPath = Path.of(storageProperties.getUploadPath(), fileHash);
            Files.createDirectories(finalPath.getParent());
            if (!Files.exists(finalPath)) {
                Files.move(assembled, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            SysFileTab record = new SysFileTab();
            record.setFilename(session.fileName);
            record.setFilehash(fileHash);
            record.setLocation(finalPath.toString());
            record.setFilesize(session.fileSize);
            record.setInuse(true);
            record.setRootmask(false);
            sysFileTabMapper.insertSelective(record);
            sysFileTab = sysFileTabService.findByHash(fileHash);
        }

        workspaceService.createFileReference(user, session.parentId, session.fileName, sysFileTab.getId(), true);
        jdbcTemplate.update(
                "UPDATE upload_session SET Status = 'COMPLETED', UpdatedAt = NOW() WHERE UploadId = :uploadId",
                new MapSqlParameterSource("uploadId", uploadId)
        );
        jdbcTemplate.update("DELETE FROM upload_part WHERE UploadId = :uploadId", new MapSqlParameterSource("uploadId", uploadId));
        FileSystemUtils.deleteRecursively(tempDir.toFile());
    }

    public UploadSessionView getSession(User user, String uploadId) {
        UploadSessionRecord session = findSession(user.getId(), uploadId);
        if (session == null) {
            throw new ApiException("NOT_FOUND", "上传会话不存在");
        }
        return toSessionView(session, loadUploadedParts(uploadId));
    }

    @Transactional
    public void cancel(User user, String uploadId) {
        UploadSessionRecord session = findSession(user.getId(), uploadId);
        if (session != null) {
            cleanupSession(session);
        }
    }

    @Transactional
    public Service storeAvatar(User user, MultipartFile file) throws Exception {
        Service root = workspaceService.ensureRoot(user);
        if (user.getImgserviceid() != null && user.getImgserviceid() > 0) {
            workspaceService.delete(user, user.getImgserviceid());
            user = userMapper.selectByPrimaryKey(user.getId());
        }

        Path tempDir = resolveTempDir("avatar-" + UUID.randomUUID());
        Files.createDirectories(tempDir);
        Path tempFile = tempDir.resolve("avatar-upload.bin");
        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile.toFile()))) {
            outputStream.write(file.getBytes());
        }

        String fileHash = FileHashUtils.md5Hex(new BufferedInputStream(new FileInputStream(tempFile.toFile())));
        SysFileTab sysFileTab = sysFileTabService.findByHash(fileHash);
        if (sysFileTab == null) {
            Path finalPath = Path.of(storageProperties.getUploadPath(), fileHash);
            Files.createDirectories(finalPath.getParent());
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
            SysFileTab record = new SysFileTab();
            record.setFilename(file.getOriginalFilename());
            record.setFilehash(fileHash);
            record.setLocation(finalPath.toString());
            record.setFilesize(file.getSize());
            record.setInuse(true);
            record.setRootmask(false);
            sysFileTabMapper.insertSelective(record);
            sysFileTab = sysFileTabService.findByHash(fileHash);
        }

        FileSystemUtils.deleteRecursively(tempDir.toFile());
        String fileName = workspaceService.findAvailableName(
                user,
                root.getId(),
                Objects.requireNonNullElse(file.getOriginalFilename(), "avatar.bin")
        );
        return workspaceService.createFileReference(user, root.getId(), fileName, sysFileTab.getId(), true);
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void cleanupExpiredSessions() {
        List<UploadSessionRecord> expiredSessions = jdbcTemplate.query(
                "SELECT * FROM upload_session WHERE ExpiresAt < NOW() AND Status <> 'COMPLETED'",
                uploadSessionMapper
        );
        for (UploadSessionRecord session : expiredSessions) {
            cleanupSession(session);
        }
    }

    private void mergeParts(Path tempDir, List<Integer> uploadedParts, Path assembled) throws IOException {
        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(assembled.toFile()))) {
            for (Integer partNumber : uploadedParts) {
                Path partPath = tempDir.resolve(partNumber + ".part");
                try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(partPath.toFile()))) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                }
            }
        }
    }

    private void cleanupSession(UploadSessionRecord session) {
        jdbcTemplate.update("DELETE FROM upload_part WHERE UploadId = :uploadId", new MapSqlParameterSource("uploadId", session.uploadId));
        jdbcTemplate.update("DELETE FROM upload_session WHERE UploadId = :uploadId", new MapSqlParameterSource("uploadId", session.uploadId));
        FileSystemUtils.deleteRecursively(new File(session.tempDir));
    }

    private UploadSessionRecord requireActiveSession(User user, String uploadId) {
        UploadSessionRecord session = findSession(user.getId(), uploadId);
        if (session == null || "CANCELED".equals(session.status)) {
            throw new ApiException("NOT_FOUND", "上传会话不存在");
        }
        if (isExpired(session)) {
            cleanupSession(session);
            throw new ApiException("EXPIRED", "上传会话已过期");
        }
        return session;
    }

    private UploadSessionRecord findSession(Integer userId, String uploadId) {
        List<UploadSessionRecord> records = jdbcTemplate.query(
                "SELECT * FROM upload_session WHERE UploadId = :uploadId AND UserID = :userId",
                new MapSqlParameterSource().addValue("uploadId", uploadId).addValue("userId", userId),
                uploadSessionMapper
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private UploadSessionRecord findSessionByClientId(Integer userId, String clientId) {
        List<UploadSessionRecord> records = jdbcTemplate.query(
                "SELECT * FROM upload_session WHERE ClientId = :clientId AND UserID = :userId",
                new MapSqlParameterSource().addValue("clientId", clientId).addValue("userId", userId),
                uploadSessionMapper
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private List<Integer> loadUploadedParts(String uploadId) {
        return jdbcTemplate.query(
                "SELECT PartNumber FROM upload_part WHERE UploadId = :uploadId ORDER BY PartNumber ASC",
                new MapSqlParameterSource("uploadId", uploadId),
                (resultSet, rowNum) -> resultSet.getInt("PartNumber")
        );
    }

    private void touchSession(String uploadId, String status) {
        jdbcTemplate.update(
                "UPDATE upload_session SET Status = :status, UpdatedAt = NOW() WHERE UploadId = :uploadId",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("uploadId", uploadId)
        );
    }

    private UploadSessionView toSessionView(UploadSessionRecord record, List<Integer> uploadedParts) {
        UploadSessionView view = new UploadSessionView();
        view.setUploadId(record.uploadId);
        view.setClientId(record.clientId);
        view.setTargetParentId(record.parentId);
        view.setChunkSize(record.chunkSize);
        view.setTotalParts(record.totalParts);
        view.setUploadedParts(uploadedParts);
        view.setExpiresAt(record.expiresAt.atZone(ZoneId.systemDefault()).toInstant().toString());
        return view;
    }

    private Path resolveTempDir(String uploadId) {
        return Path.of(storageProperties.getUploadPath(), storageProperties.getTempDirName(), uploadId);
    }

    private boolean isExpired(UploadSessionRecord session) {
        return session.expiresAt.isBefore(LocalDateTime.now());
    }

    private String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        return relativePath.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private static class UploadSessionRecord {
        private String uploadId;
        private Integer userId;
        private String clientId;
        private Integer parentId;
        private String relativePath;
        private String fileName;
        private long fileSize;
        private int chunkSize;
        private int totalParts;
        private String fileHashHint;
        private String status;
        private String tempDir;
        private LocalDateTime expiresAt;
    }
}
