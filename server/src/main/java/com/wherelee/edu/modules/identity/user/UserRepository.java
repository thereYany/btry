package com.wherelee.edu.modules.identity.user;

import com.wherelee.edu.modules.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("nickname"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getBoolean("must_change_password"),
            rs.getInt("token_version")
    );

    private final JdbcTemplate jdbc;
    private final com.wherelee.edu.modules.identity.rbac.RbacRepository rbacRepository;

    public UserRepository(JdbcTemplate jdbc,
                          com.wherelee.edu.modules.identity.rbac.RbacRepository rbacRepository) {
        this.jdbc = jdbc;
        this.rbacRepository = rbacRepository;
    }

    public Optional<User> findByUsername(String username) {
        List<User> users = jdbc.query("""
                SELECT id, tenant_id, username, password_hash, nickname, role, status,
                       must_change_password, token_version
                FROM t_user
                WHERE username = ?
                """, MAPPER, username);
        return users.stream().findFirst();
    }

    public Optional<User> findById(Long id) {
        List<User> users = jdbc.query("""
                SELECT id, tenant_id, username, password_hash, nickname, role, status,
                       must_change_password, token_version
                FROM t_user
                WHERE id = ?
                """, MAPPER, id);
        return users.stream().findFirst();
    }

    public void updatePassword(Long id, String passwordHash) {
        jdbc.update("""
                UPDATE t_user
                SET password_hash = ?,
                    must_change_password = 0,
                    token_version = token_version + 1,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, passwordHash, id);
    }

    public int disable(Long id) {
        return jdbc.update("""
                UPDATE t_user
                SET status = 'DISABLED', token_version = token_version + 1,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, id);
    }

    public int enable(Long id) {
        return jdbc.update("""
                UPDATE t_user
                SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, id);
    }

    public int resetPassword(Long id, String passwordHash) {
        return jdbc.update("""
                UPDATE t_user
                SET password_hash = ?, must_change_password = 1,
                    token_version = token_version + 1,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, passwordHash, id);
    }
    public Long create(String username, String passwordHash, String nickname,
                       String role, boolean mustChangePassword) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_user
                        (tenant_id, username, password_hash, nickname, role, status,
                         must_change_password, token_version)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, TenantContext.current());
            statement.setString(2, username);
            statement.setString(3, passwordHash);
            statement.setString(4, nickname);
            statement.setString(5, role);
            statement.setBoolean(6, mustChangePassword);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建账号后未返回用户 ID");
        }
        long userId = key.longValue();
        // 新用户同步写入 user_role，权限解析不再依赖 t_user.role 单一字段
        rbacRepository.bindRole(userId, role);
        return userId;
    }
}
