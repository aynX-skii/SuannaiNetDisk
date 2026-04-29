package com.suannai.netdisk.v2.uploads;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.common.util.FileHashUtils;
import com.suannai.netdisk.common.util.SessionUser;
import com.suannai.netdisk.config.StorageProperties;
import com.suannai.netdisk.service.AppSettingsService;
import com.suannai.netdisk.v2.workspace.WorkspaceService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UploadService {
    private static final int DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final WorkspaceService workspaceService;
    private final StorageProperties storageProperties;
    private final AppSettingsService appSettingsService;

    private final RowMapper<UploadSessionRecord> uploadSessionMapper = (resultSet, rowNum) -> new UploadSessionRecord(
            resultSet.getString("upload_id"),
            resultSet.getLong("user_id"),
            resultSet.getString("client_id"),
            resultSet.getLong("parent_id"),
            resultSet.getString("relative_path"),
            resultSet.getString("file_name"),
            resultSet.getLong("file_size"),
            resultSet.getInt("chunk_size"),
            resultSet.getInt("total_parts"),
            resultSet.getString("file_md5_hint"),
            resultSet.getString("status"),
            resultSet.getString("temp_dir"),
            resultSet.getTimestamp("expires_at").toLocalDateTime()
    );

    private final RowMapper<StorageObjectRecord> storageObjectMapper = (resultSet, rowNum) -> new StorageObjectRecord(
            resultSet.getLong("id"),
            resultSet.getString("sha256"),
            resultSet.getString("md5"),
            resultSet.getString("storage_path"),
            resultSet.getLong("file_size")
    );

    public UploadService(NamedParameterJdbcTemplate jdbcTemplate,
                         WorkspaceService workspaceService,
                         StorageProperties storageProperties,
                         AppSettingsService appSettingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.workspaceService = workspaceService;
        this.storageProperties = storageProperties;
        this.appSettingsService = appSettingsService;
    }

    @Transactional
    public PrepareUploadResponse prepare(SessionUser user, PrepareUploadRequest request) {
        if (!appSettingsService.isEnabled("allow_upload")) {
            throw new ApiException("FORBIDDEN", "管理员已关闭上传");
        }

        WorkspaceService.EntryRecord parent = workspaceService.getDirectoryOrRoot(user.id(), request.getParentId());
        PrepareUploadResponse response = new PrepareUploadResponse();
        List<WorkspaceService.DirectoryCreatedView> createdDirectories = new ArrayList<>();

        List<PrepareUploadDirectoryRequest> directoryRequests = request.getDirectories() == null
                ? List.of()
                : request.getDirectories();
        directoryRequests.stream()
                .sorted(Comparator.comparingInt(entry -> normalizeRelativePath(entry.getPath()).split("/").length))
                .forEach(entry -> workspaceService.ensureDirectoryPath(
                        user,
                        parent.id(),
                        normalizeRelativePath(entry.getPath()),
                        createdDirectories
                ));

        List<PrepareUploadFileRequest> fileRequests = request.getFiles() == null ? List.of() : request.getFiles();
        for (PrepareUploadFileRequest file : fileRequests) {
            String relativePath = normalizeRelativePath(file.getRelativePath());
            Long targetParentId = workspaceService.ensureDirectoryPath(user, parent.id(), relativePath, createdDirectories);

            if (workspaceService.existsSibling(user, targetParentId, file.getName())) {
                response.getConflicts().add(new PrepareUploadResponse.ConflictPayload(
                        file.getClientId(),
                        relativePath.isBlank() ? file.getName() : relativePath + "/" + file.getName(),
                        "同目录已存在同名文件或目录"
                ));
                continue;
            }

            if (file.getMd5() != null && !file.getMd5().isBlank()) {
                StorageObjectRecord object = findStorageObjectByMd5(file.getMd5());
                if (object != null) {
                    WorkspaceService.EntryRecord entry = workspaceService.createFileReference(user, targetParentId, file.getName(), object.id());
                    response.getInstantFiles().add(new PrepareUploadResponse.InstantFilePayload(file.getClientId(), entry.id(), "秒传成功"));
                    continue;
                }
            }

            UploadSessionRecord existingSession = findSessionByClientId(user.id(), file.getClientId());
            if (existingSession != null && !isExpired(existingSession) && !"COMPLETED".equals(existingSession.status()) && !"CANCELED".equals(existingSession.status())) {
                response.getUploadSessions().add(toSessionView(existingSession, loadUploadedParts(existingSession.uploadId())));
                continue;
            }
            if (existingSession != null) {
                cleanupSession(existingSession);
            }

            String uploadId = UUID.randomUUID().toString().replace("-", "");
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(storageProperties.getUploadSessionTtlHours());
            int totalParts = file.getSize() == 0 ? 0 : (int) Math.ceil((double) file.getSize() / DEFAULT_CHUNK_SIZE);

            jdbcTemplate.update(
                    "INSERT INTO upload_sessions(upload_id, user_id, client_id, parent_id, relative_path, file_name, file_size, " +
                            "chunk_size, total_parts, file_md5_hint, status, temp_dir, expires_at) " +
                            "VALUES(:uploadId, :userId, :clientId, :parentId, :relativePath, :fileName, :fileSize, :chunkSize, " +
                            ":totalParts, :fileMd5Hint, 'PENDING', :tempDir, :expiresAt)",
                    new MapSqlParameterSource()
                            .addValue("uploadId", uploadId)
                            .addValue("userId", user.id())
                            .addValue("clientId", file.getClientId())
                            .addValue("parentId", targetParentId)
                            .addValue("relativePath", relativePath)
                            .addValue("fileName", file.getName())
                            .addValue("fileSize", file.getSize())
                            .addValue("chunkSize", DEFAULT_CHUNK_SIZE)
                            .addValue("totalParts", totalParts)
                            .addValue("fileMd5Hint", normalizeHash(file.getMd5()))
                            .addValue("tempDir", resolveTempDir(uploadId).toString())
                            .addValue("expiresAt", Timestamp.valueOf(expiresAt))
            );

            response.getUploadSessions().add(toSessionView(findSession(user.id(), uploadId), List.of()));
        }

        response.setCreatedDirectories(createdDirectories.stream()
                .map(entry -> new PrepareUploadResponse.CreatedDirectoryPayload(entry.path(), entry.directoryId()))
                .collect(Collectors.toList()));
        return response;
    }

    @Transactional
    public void storePart(SessionUser user, String uploadId, Integer partNumber, byte[] body) throws IOException {
        UploadSessionRecord session = requireActiveSession(user, uploadId);
        if (partNumber < 1 || partNumber > Math.max(1, session.totalParts())) {
            throw new ApiException("INVALID_PART", "分片序号超出范围");
        }

        Path tempDir = Path.of(session.tempDir());
        Files.createDirectories(tempDir);
        Path partPath = tempDir.resolve(partNumber + ".part");
        if (Files.exists(partPath)) {
            Long uploadedSize = jdbcTemplate.query(
                    "SELECT part_size FROM upload_parts WHERE upload_id = :uploadId AND part_number = :partNumber",
                    new MapSqlParameterSource().addValue("uploadId", uploadId).addValue("partNumber", partNumber),
                    resultSet -> resultSet.next() ? resultSet.getLong(1) : null
            );
            if (uploadedSize != null && uploadedSize == body.length) {
                return;
            }
        }

        Files.write(partPath, body);
        jdbcTemplate.update(
                "INSERT INTO upload_parts(upload_id, part_number, part_size) VALUES(:uploadId, :partNumber, :partSize) " +
                        "ON DUPLICATE KEY UPDATE part_size = VALUES(part_size)",
                new MapSqlParameterSource()
                        .addValue("uploadId", uploadId)
                        .addValue("partNumber", partNumber)
                        .addValue("partSize", body.length)
        );
        touchSession(uploadId, "UPLOADING");
    }

    @Transactional
    public void complete(SessionUser user, String uploadId) throws Exception {
        UploadSessionRecord session = requireActiveSession(user, uploadId);
        if (workspaceService.existsSibling(user, session.parentId(), session.fileName())) {
            throw new ApiException("NAME_CONFLICT", "同目录下已存在同名文件或目录");
        }

        Path tempDir = Path.of(session.tempDir());
        Files.createDirectories(tempDir);
        Path assembled = tempDir.resolve("assembled.bin");

        if (session.totalParts() == 0) {
            Files.write(assembled, new byte[0]);
        } else {
            List<Integer> uploadedParts = loadUploadedParts(uploadId);
            if (uploadedParts.size() != session.totalParts()) {
                throw new ApiException("MISSING_PARTS", "仍有分片未上传");
            }
            mergeParts(tempDir, uploadedParts, assembled);
        }

        FileHashUtils.FileHashes hashes = FileHashUtils.digest(assembled);
        StorageObjectRecord object = findStorageObjectBySha256(hashes.sha256());
        if (object == null) {
            Path finalPath = resolveObjectPath(hashes.sha256());
            Files.createDirectories(finalPath.getParent());
            if (!Files.exists(finalPath)) {
                Files.move(assembled, finalPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(assembled);
            }
            object = createStorageObject(hashes, finalPath, session.fileName(), session.fileSize());
        } else {
            Files.deleteIfExists(assembled);
        }

        workspaceService.createFileReference(user, session.parentId(), session.fileName(), object.id());
        jdbcTemplate.update(
                "UPDATE upload_sessions SET status = 'COMPLETED', updated_at = NOW() WHERE upload_id = :uploadId",
                new MapSqlParameterSource("uploadId", uploadId)
        );
        jdbcTemplate.update("DELETE FROM upload_parts WHERE upload_id = :uploadId", new MapSqlParameterSource("uploadId", uploadId));
        FileSystemUtils.deleteRecursively(tempDir.toFile());
    }

    public UploadSessionView getSession(SessionUser user, String uploadId) {
        UploadSessionRecord session = findSession(user.id(), uploadId);
        if (session == null) {
            throw new ApiException("NOT_FOUND", "上传会话不存在");
        }
        return toSessionView(session, loadUploadedParts(uploadId));
    }

    @Transactional
    public void cancel(SessionUser user, String uploadId) {
        UploadSessionRecord session = findSession(user.id(), uploadId);
        if (session != null) {
            cleanupSession(session);
        }
    }

    @Transactional
    public Long storeAvatar(SessionUser user, MultipartFile file) throws Exception {
        WorkspaceService.EntryRecord root = workspaceService.ensureRoot(user.id());

        Path tempDir = resolveTempDir("avatar-" + UUID.randomUUID());
        Files.createDirectories(tempDir);
        Path tempFile = tempDir.resolve("avatar-upload.bin");
        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile.toFile()))) {
            outputStream.write(file.getBytes());
        }

        FileHashUtils.FileHashes hashes = FileHashUtils.digest(tempFile);
        StorageObjectRecord object = findStorageObjectBySha256(hashes.sha256());
        if (object == null) {
            Path finalPath = resolveObjectPath(hashes.sha256());
            Files.createDirectories(finalPath.getParent());
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
            object = createStorageObject(hashes, finalPath, file.getOriginalFilename(), file.getSize());
        }

        FileSystemUtils.deleteRecursively(tempDir.toFile());
        String fileName = workspaceService.findAvailableName(
                user,
                root.id(),
                Objects.requireNonNullElse(file.getOriginalFilename(), "avatar.bin")
        );
        return workspaceService.createFileReference(user, root.id(), fileName, object.id()).id();
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void cleanupExpiredSessions() {
        List<UploadSessionRecord> expiredSessions = jdbcTemplate.query(
                "SELECT * FROM upload_sessions WHERE expires_at < NOW() AND status <> 'COMPLETED'",
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
                    inputStream.transferTo(outputStream);
                }
            }
        }
    }

    private StorageObjectRecord createStorageObject(FileHashUtils.FileHashes hashes, Path finalPath, String originalName, long fileSize) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                "INSERT INTO storage_objects(sha256, md5, storage_key, storage_path, original_name, file_size, ref_count) " +
                        "VALUES(:sha256, :md5, :storageKey, :storagePath, :originalName, :fileSize, 0)",
                new MapSqlParameterSource()
                        .addValue("sha256", hashes.sha256())
                        .addValue("md5", hashes.md5())
                        .addValue("storageKey", hashes.sha256())
                        .addValue("storagePath", finalPath.toString())
                        .addValue("originalName", originalName)
                        .addValue("fileSize", fileSize),
                keyHolder,
                new String[]{"id"}
        );
        return new StorageObjectRecord(keyHolder.getKey().longValue(), hashes.sha256(), hashes.md5(), finalPath.toString(), fileSize);
    }

    private StorageObjectRecord findStorageObjectByMd5(String md5) {
        if (md5 == null || md5.isBlank()) {
            return null;
        }
        List<StorageObjectRecord> records = jdbcTemplate.query(
                "SELECT id, sha256, md5, storage_path, file_size FROM storage_objects WHERE md5 = :md5 ORDER BY id LIMIT 1",
                new MapSqlParameterSource("md5", normalizeHash(md5)),
                storageObjectMapper
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private StorageObjectRecord findStorageObjectBySha256(String sha256) {
        List<StorageObjectRecord> records = jdbcTemplate.query(
                "SELECT id, sha256, md5, storage_path, file_size FROM storage_objects WHERE sha256 = :sha256",
                new MapSqlParameterSource("sha256", sha256),
                storageObjectMapper
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private void cleanupSession(UploadSessionRecord session) {
        jdbcTemplate.update("DELETE FROM upload_sessions WHERE upload_id = :uploadId", new MapSqlParameterSource("uploadId", session.uploadId()));
        FileSystemUtils.deleteRecursively(new File(session.tempDir()));
    }

    private UploadSessionRecord requireActiveSession(SessionUser user, String uploadId) {
        UploadSessionRecord session = findSession(user.id(), uploadId);
        if (session == null || "CANCELED".equals(session.status())) {
            throw new ApiException("NOT_FOUND", "上传会话不存在");
        }
        if (isExpired(session)) {
            cleanupSession(session);
            throw new ApiException("EXPIRED", "上传会话已过期");
        }
        return session;
    }

    private UploadSessionRecord findSession(Long userId, String uploadId) {
        List<UploadSessionRecord> records = jdbcTemplate.query(
                "SELECT * FROM upload_sessions WHERE upload_id = :uploadId AND user_id = :userId",
                new MapSqlParameterSource().addValue("uploadId", uploadId).addValue("userId", userId),
                uploadSessionMapper
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private UploadSessionRecord findSessionByClientId(Long userId, String clientId) {
        List<UploadSessionRecord> records = jdbcTemplate.query(
                "SELECT * FROM upload_sessions WHERE client_id = :clientId AND user_id = :userId",
                new MapSqlParameterSource().addValue("clientId", clientId).addValue("userId", userId),
                uploadSessionMapper
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private List<Integer> loadUploadedParts(String uploadId) {
        return jdbcTemplate.query(
                "SELECT part_number FROM upload_parts WHERE upload_id = :uploadId ORDER BY part_number ASC",
                new MapSqlParameterSource("uploadId", uploadId),
                (resultSet, rowNum) -> resultSet.getInt("part_number")
        );
    }

    private void touchSession(String uploadId, String status) {
        jdbcTemplate.update(
                "UPDATE upload_sessions SET status = :status, updated_at = NOW() WHERE upload_id = :uploadId",
                new MapSqlParameterSource().addValue("status", status).addValue("uploadId", uploadId)
        );
    }

    private UploadSessionView toSessionView(UploadSessionRecord record, List<Integer> uploadedParts) {
        UploadSessionView view = new UploadSessionView();
        view.setUploadId(record.uploadId());
        view.setClientId(record.clientId());
        view.setTargetParentId(record.parentId());
        view.setChunkSize(record.chunkSize());
        view.setTotalParts(record.totalParts());
        view.setUploadedParts(uploadedParts);
        view.setExpiresAt(record.expiresAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        return view;
    }

    private Path resolveTempDir(String uploadId) {
        return Path.of(storageProperties.getUploadPath(), storageProperties.getTempDirName(), uploadId);
    }

    private Path resolveObjectPath(String sha256) {
        return Path.of(storageProperties.getUploadPath(), sha256.substring(0, 2), sha256);
    }

    private boolean isExpired(UploadSessionRecord session) {
        return session.expiresAt().isBefore(LocalDateTime.now());
    }

    private String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        return relativePath.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String normalizeHash(String hash) {
        return hash == null || hash.isBlank() ? null : hash.trim().toLowerCase();
    }

    private record UploadSessionRecord(String uploadId,
                                       Long userId,
                                       String clientId,
                                       Long parentId,
                                       String relativePath,
                                       String fileName,
                                       long fileSize,
                                       int chunkSize,
                                       int totalParts,
                                       String fileHashHint,
                                       String status,
                                       String tempDir,
                                       LocalDateTime expiresAt) {
    }

    private record StorageObjectRecord(Long id, String sha256, String md5, String storagePath, long fileSize) {
    }
}
