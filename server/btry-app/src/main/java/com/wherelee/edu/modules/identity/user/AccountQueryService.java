package com.wherelee.edu.modules.identity.user;

import com.wherelee.edu.modules.contracts.identity.AccountQuery;
import com.wherelee.edu.modules.platform.common.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 账号分页查询实现：过滤与分页都在身份域内完成。
 */
@Service
public class AccountQueryService implements AccountQuery {

    private static final RowMapper<AccountProfile> MAPPER = (rs, rowNum) -> new AccountProfile(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("nickname"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getBoolean("must_change_password"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public AccountQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<AccountProfile> page(String keyword, String role, String status, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = where(keyword, role, status, args);
        args.add(limit);
        args.add(offset);
        List<AccountProfile> records = jdbc.query("""
                SELECT id, username, nickname, role, status, must_change_password, created_at
                FROM t_user
                """ + where + " ORDER BY id DESC LIMIT ? OFFSET ?", MAPPER, args.toArray());
        long total = count(keyword, role, status);
        return new PageResult<>(records, total, offset / Math.max(1, limit) + 1, limit);
    }

    @Override
    public long count(String keyword, String role, String status) {
        List<Object> args = new ArrayList<>();
        String where = where(keyword, role, status, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_user " + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private static String where(String keyword, String role, String status, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(username LIKE ? OR nickname LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (role != null && !role.isBlank()) {
            conditions.add("role = ?");
            args.add(role);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            args.add(status);
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }
}
