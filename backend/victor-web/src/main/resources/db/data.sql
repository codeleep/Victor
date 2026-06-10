-- =====================================================
-- Victor AI 面试助手 - 系统初始数据 (DML)
-- 基于 03-数据库设计.md
-- =====================================================

-- 重置序列，确保下一个用户id从1开始
SELECT setval('user_id_seq', 1, false);

-- 插入默认管理员账号 (密码: admin123, BCrypt加密)
INSERT INTO "user" (username, email, password_hash, nickname, status)
VALUES ('admin', 'admin@example.com', '$2a$10$mzkLmuSgvypGtUMxAkr60OUmNTuw1ZJWWpvL5M5Lkfwru5vsQfHFa', '管理员', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;
