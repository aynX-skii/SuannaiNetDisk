SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `sysconfig`(
  `ID` int primary key auto_increment,
  `Name` varchar(255) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL UNIQUE,
  `Value` varchar(255) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL
)ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `sysfiletab`(
  `ID` int primary key auto_increment,
  `FileName` varchar(255) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `FileHash` varchar(32) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL UNIQUE,
  `Location` varchar(9999) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `FileSize` bigint NOT NULL,
  `InUse` bit(1) NOT NULL,
  `RootMask` bit(1) NOT NULL default 0x0
)ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `users`(
  `ID` int primary key auto_increment,
  `UserName` varchar(255) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL UNIQUE,
  `Password` varchar(100) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `PasswordAlgo` varchar(20) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'MD5',
  `LastLoginTime` datetime,
  `LastLoginIP` varchar(255) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `Status` bit(1) NOT NULL default 1,
  `ImgServiceID` int NOT NULL  default -1,
  `NickName` varchar(50) default 'Net'
)ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `service`(
  `ID` int primary key auto_increment,
  `UserID` int NOT NULL,
  `UserFileName` varchar(255) character set utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `SysFileRecordID` int NOT NULL,
  `Status` bit(1) NOT NULL default 1,
  `UploadDate` datetime NOT NULL default CURRENT_TIMESTAMP,
  `ParentID` int,
  `DirMask` bit(1) NOT NULL default 0
)ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

INSERT IGNORE INTO `sysconfig`(`Name`,`Value`) VALUES('AllowLogin','YES');
INSERT IGNORE INTO `sysconfig`(`Name`,`Value`) VALUES('AllowRegister','YES');
INSERT IGNORE INTO `sysconfig`(`Name`,`Value`) VALUES('AllowUpload','YES');
INSERT IGNORE INTO `sysconfig`(`Name`,`Value`) VALUES('AllowDownload','YES');
INSERT IGNORE INTO `sysconfig`(`Name`,`Value`) VALUES('AllowLogout','YES');
INSERT IGNORE INTO `sysconfig`(`Name`,`Value`) VALUES('SysFileTabAllowErase','YES');
