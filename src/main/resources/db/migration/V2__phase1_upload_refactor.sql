CREATE TABLE IF NOT EXISTS `upload_session`(
  `UploadId` varchar(64) PRIMARY KEY,
  `UserID` int NOT NULL,
  `ClientId` varchar(64) NOT NULL,
  `ParentID` int NOT NULL,
  `RelativePath` varchar(1024) NOT NULL DEFAULT '',
  `FileName` varchar(255) NOT NULL,
  `FileSize` bigint NOT NULL,
  `ChunkSize` int NOT NULL,
  `TotalParts` int NOT NULL,
  `FileHashHint` varchar(32),
  `Status` varchar(20) NOT NULL,
  `TempDir` varchar(1024) NOT NULL,
  `ExpiresAt` datetime NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_upload_session_user_client` (`UserID`, `ClientId`)
)ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `upload_part`(
  `ID` bigint PRIMARY KEY AUTO_INCREMENT,
  `UploadId` varchar(64) NOT NULL,
  `PartNumber` int NOT NULL,
  `PartSize` bigint NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_upload_part_upload_part_number` (`UploadId`, `PartNumber`)
)ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

CREATE INDEX `idx_service_user_parent` ON `service` (`UserID`, `ParentID`);
CREATE INDEX `idx_service_user_parent_name` ON `service` (`UserID`, `ParentID`, `UserFileName`);
CREATE INDEX `idx_service_sys_file_record` ON `service` (`SysFileRecordID`);
