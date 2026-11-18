package com.wherelee.edu.integration;

import com.wherelee.edu.BtryApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * 内核集成测试基座：只准备身份/支付内核数据，不涉及任何垂直业务表。
 */
@Tag("integration")
@SpringBootTest(classes = BtryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
public abstract class KernelIntegrationTestSupport {

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetKernel() {
        truncateAll();
    }

    protected void truncateAll() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : new String[]{
                "t_user", "t_user_role", "t_user_invitation",
                "t_payment_order", "t_payment_attempt", "t_payment_event", "t_refund",
                "t_payment_outbox", "t_recon_task", "t_recon_diff", "t_channel_bill"
        }) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        // 内置角色/权限由内核迁移提供；自定义角色属于测试数据，逐次清理
        List<Long> customRoleIds = jdbc.queryForList(
                "SELECT id FROM t_role WHERE built_in = 0", Long.class);
        if (!customRoleIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(customRoleIds.size(), "?"));
            Object[] ids = customRoleIds.toArray();
            jdbc.update("DELETE FROM t_role_permission WHERE role_id IN (" + placeholders + ")", ids);
            jdbc.update("DELETE FROM t_user_role WHERE role_id IN (" + placeholders + ")", ids);
            jdbc.update("DELETE FROM t_role WHERE id IN (" + placeholders + ")", ids);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /** 创建账号并绑定已存在的角色；角色由内核迁移里的种子数据提供。 */
    protected Long seedUser(String username, String rawPassword, String nickname, String roleCode) {
        jdbc.update("""
                INSERT INTO t_user
                    (tenant_id, username, password_hash, nickname, role, status,
                     must_change_password, token_version)
                VALUES (0, ?, ?, ?, ?, 'ACTIVE', FALSE, 0)
                """, username, passwordEncoder.encode(rawPassword), nickname, roleCode);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM t_user WHERE tenant_id = 0 AND username = ?", Long.class, username);
        jdbc.update("""
                INSERT IGNORE INTO t_user_role (tenant_id, user_id, role_id)
                SELECT 0, ?, r.id FROM t_role r WHERE r.tenant_id = 0 AND r.code = ?
                """, userId, roleCode);
        return userId;
    }

    protected int count(String table, String where, Object... args) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + (where == null ? "" : " WHERE " + where),
                Integer.class, args);
        return value == null ? 0 : value;
    }
}
