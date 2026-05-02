# 后端部署说明

这个仓库只负责 NetDisk API 服务。生产打包不会包含或代理前端 SPA 页面，前端应由 `NetDisk-Front` 构建后交给 nginx 部署。

## 生产运行

```powershell
$env:NETDISK_DB_PASSWORD='your-password'
java -jar target/NetDisk-0.0.1-SNAPSHOT.jar
```

常用环境变量：

- `NETDISK_DB_URL`: MySQL 连接串
- `NETDISK_DB_USERNAME`: MySQL 用户名
- `NETDISK_DB_PASSWORD`: MySQL 密码
- `NETDISK_UPLOAD_PATH`: 文件存储路径
- `NETDISK_CORS_ALLOWED_ORIGINS`: 前后端跨域部署时允许的前端来源，例如 `https://netdisk.example.com`
- `NETDISK_SESSION_COOKIE_SECURE`: HTTPS 环境建议设为 `true`
- `NETDISK_SESSION_COOKIE_SAME_SITE`: 跨站 Cookie 场景可按网关策略调整

## 多实例要求

后端支持多实例共享登录态：会话由 Spring Session JDBC 存入 MySQL，Flyway 的 `V2__shared_jdbc_sessions.sql` 会创建会话表。

当前文件内容仍存储在文件系统中。多台后端服务器同时部署时，`NETDISK_UPLOAD_PATH` 必须指向所有实例都能访问的共享存储，例如 NAS/NFS 挂载目录；否则某个实例上传的文件，其他实例无法读取。

## nginx 前端

如果 nginx 将 `/api/` 反向代理到后端，可以不启用 CORS；如果前端和 API 是不同 Origin，需要设置 `NETDISK_CORS_ALLOWED_ORIGINS` 并确保 Cookie 策略与 HTTPS 配置匹配。
