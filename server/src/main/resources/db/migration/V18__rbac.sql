-- P3 身份通用化：角色/权限落表，接口鉴权从硬编码角色改为权限码
-- 设计：权限（permission）是稳定契约，角色（role）是租户可配置的权限集合，用户可有多个角色。

CREATE TABLE t_role
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    code        VARCHAR(40)  NOT NULL COMMENT '角色码：ADMIN/TEACHER/STUDENT 或租户自定义',
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(200) NULL,
    built_in    TINYINT      NOT NULL DEFAULT 0 COMMENT '内置角色不允许删除',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_tenant_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色';

CREATE TABLE t_permission
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    code        VARCHAR(80)  NOT NULL COMMENT '权限码，形如 order:pay',
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(200) NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_tenant_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '权限点';

CREATE TABLE t_role_permission
(
    id            BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT NOT NULL DEFAULT 0,
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (tenant_id, role_id, permission_id),
    KEY idx_rp_role (tenant_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色-权限';

CREATE TABLE t_user_role
(
    id        BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id   BIGINT NOT NULL,
    role_id   BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (tenant_id, user_id, role_id),
    KEY idx_ur_user (tenant_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户-角色（支持多角色）';

-- 邀请通用化：不再硬编码 TEACHER
ALTER TABLE t_teacher_invitation
    ADD COLUMN role_code VARCHAR(40) NOT NULL DEFAULT 'TEACHER' COMMENT '受邀角色码' AFTER nickname;

-- 内置角色（tenant 0）
INSERT INTO t_role (tenant_id, code, name, description, built_in)
VALUES (0, 'ADMIN', '管理员', '平台管理员，拥有全部权限', 1),
       (0, 'TEACHER', '教师', '内容创作与发布', 1),
       (0, 'STUDENT', '学员', '购买课程与学习', 1);

-- 权限点（接口鉴权的最小单位）
INSERT INTO t_permission (tenant_id, code, name)
VALUES (0, 'course:catalog:read', '查看课程目录'),
       (0, 'course:series:read', '查看课程详情'),
       (0, 'order:create', '创建订单'),
       (0, 'order:read', '查看订单'),
       (0, 'order:pay', '发起支付'),
       (0, 'order:cancel', '取消订单'),
       (0, 'payment:mock', '模拟支付（测试用）'),
       (0, 'learning:progress:write', '上报学习进度'),
       (0, 'teacher:series:manage', '管理本人系列'),
       (0, 'teacher:content:manage', '管理系列内容'),
       (0, 'teacher:video:upload', '上传视频'),
       (0, 'admin:user:manage', '管理账号'),
       (0, 'admin:order:manage', '管理订单'),
       (0, 'admin:series:manage', '治理课程'),
       (0, 'admin:invitation:manage', '管理邀请'),
       (0, 'admin:recon:manage', '对账管理'),
       (0, 'admin:role:manage', '管理角色与权限');

-- 角色 → 权限
INSERT INTO t_role_permission (tenant_id, role_id, permission_id)
SELECT 0, r.id, p.id
FROM t_role r
         JOIN t_permission p ON p.tenant_id = 0
WHERE r.tenant_id = 0
  AND (
    r.code = 'ADMIN'
        OR (r.code = 'TEACHER' AND p.code IN (
            'course:catalog:read', 'course:series:read',
            'teacher:series:manage', 'teacher:content:manage', 'teacher:video:upload'))
        OR (r.code = 'STUDENT' AND p.code IN (
            'course:catalog:read', 'course:series:read',
            'order:create', 'order:read', 'order:pay', 'order:cancel', 'payment:mock',
            'learning:progress:write'))
    );

-- 回填现有用户角色
INSERT INTO t_user_role (tenant_id, user_id, role_id)
SELECT u.tenant_id, u.id, r.id
FROM t_user u
         JOIN t_role r ON r.tenant_id = u.tenant_id AND r.code = u.role;
