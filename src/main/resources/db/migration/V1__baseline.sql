CREATE TABLE app_settings (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(100) NOT NULL,
  setting_value VARCHAR(255) NOT NULL,
  description VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_settings_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  password_algo VARCHAR(20) NOT NULL DEFAULT 'BCRYPT',
  nickname VARCHAR(64) NOT NULL,
  avatar_entry_id BIGINT UNSIGNED,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at DATETIME,
  last_login_ip VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE storage_objects (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  sha256 CHAR(64) NOT NULL,
  md5 CHAR(32),
  storage_key VARCHAR(255) NOT NULL,
  storage_path VARCHAR(1024) NOT NULL,
  original_name VARCHAR(255),
  mime_type VARCHAR(255),
  file_size BIGINT UNSIGNED NOT NULL,
  ref_count INT UNSIGNED NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_storage_objects_sha256 (sha256),
  UNIQUE KEY uk_storage_objects_storage_key (storage_key),
  KEY idx_storage_objects_md5 (md5),
  KEY idx_storage_objects_ref_count (ref_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_entries (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  parent_id BIGINT UNSIGNED,
  parent_key BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(parent_id, 0)) STORED,
  storage_object_id BIGINT UNSIGNED,
  name VARCHAR(255) NOT NULL,
  entry_type ENUM('DIRECTORY', 'FILE') NOT NULL,
  status ENUM('ACTIVE', 'DELETED') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  CONSTRAINT fk_file_entries_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_file_entries_storage_object FOREIGN KEY (storage_object_id) REFERENCES storage_objects(id) ON DELETE RESTRICT,
  CONSTRAINT ck_file_entries_type_object CHECK (
    (entry_type = 'DIRECTORY' AND storage_object_id IS NULL) OR
    (entry_type = 'FILE' AND storage_object_id IS NOT NULL)
  ),
  UNIQUE KEY uk_file_entries_sibling (user_id, parent_key, name),
  KEY idx_file_entries_parent (user_id, parent_id, entry_type, name),
  KEY idx_file_entries_storage_object (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE upload_sessions (
  upload_id CHAR(32) PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  client_id VARCHAR(64) NOT NULL,
  parent_id BIGINT UNSIGNED NOT NULL,
  relative_path VARCHAR(1024) NOT NULL DEFAULT '',
  file_name VARCHAR(255) NOT NULL,
  file_size BIGINT UNSIGNED NOT NULL,
  chunk_size INT UNSIGNED NOT NULL,
  total_parts INT UNSIGNED NOT NULL,
  file_md5_hint CHAR(32),
  status ENUM('PENDING', 'UPLOADING', 'COMPLETED', 'CANCELED') NOT NULL DEFAULT 'PENDING',
  temp_dir VARCHAR(1024) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_upload_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_upload_sessions_parent FOREIGN KEY (parent_id) REFERENCES file_entries(id) ON DELETE CASCADE,
  UNIQUE KEY uk_upload_sessions_user_client (user_id, client_id),
  KEY idx_upload_sessions_expiry (expires_at, status),
  KEY idx_upload_sessions_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE upload_parts (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  upload_id CHAR(32) NOT NULL,
  part_number INT UNSIGNED NOT NULL,
  part_size BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_upload_parts_session FOREIGN KEY (upload_id) REFERENCES upload_sessions(upload_id) ON DELETE CASCADE,
  UNIQUE KEY uk_upload_parts_number (upload_id, part_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO app_settings(setting_key, setting_value, description) VALUES
  ('allow_login', 'YES', '允许用户登录'),
  ('allow_register', 'YES', '允许用户注册'),
  ('allow_upload', 'YES', '允许上传文件'),
  ('allow_download', 'YES', '允许下载文件'),
  ('allow_delete_storage_object', 'YES', '无引用时允许删除物理文件');
