package com.wherelee.edu.modules.platform.tenant;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 给单表 SQL 注入租户条件。
 *
 * <p>只处理形态可控的语句：单表、无 JOIN、无子查询。其余语句必须由开发者手写租户条件，
 * 否则在 enforce 模式下直接抛错——宁可启动/调用失败，也不允许"忘了加租户条件"静默通过。</p>
 */
public final class TenantSqlRewriter {

    private static final Set<String> TENANT_TABLES = Set.of(
            "t_user", "t_teacher_invitation", "t_series", "t_series_chapter", "t_series_video",
            "t_student_series", "t_learning_progress", "t_playback_log",
            "t_order", "t_payment_attempt", "t_payment_event", "t_refund",
            "t_alert_log", "t_admin_audit_log", "t_payment_outbox", "t_payment_order");

    private static final Pattern INSERT = Pattern.compile("\\binsert\\s+into\\s+(t_[a-z_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE = Pattern.compile("\\bupdate\\s+(t_[a-z_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM = Pattern.compile("\\bfrom\\s+(t_[a-z_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE = Pattern.compile("\\bdelete\\s+from\\s+(t_[a-z_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAIL = Pattern.compile(
            "\\b(order\\s+by|group\\s+by|limit|for\\s+update)\\b", Pattern.CASE_INSENSITIVE);

    private TenantSqlRewriter() {
    }

    /** 重写结果：新 SQL + 租户占位符应插入到参数数组的哪个下标。 */
    public record RewriteResult(String sql, int argIndex) {
        public boolean changed() {
            return argIndex >= 0;
        }
    }

    /** 返回注入租户条件后的结果；changed()=false 表示不需要处理。无法安全处理时抛异常。 */
    public static RewriteResult rewrite(String sql) {
        if (sql == null || sql.isBlank()) {
            return new RewriteResult(sql, -1);
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        if (lower.contains("tenant_id")) {
            return new RewriteResult(sql, -1);
        }
        Matcher insert = INSERT.matcher(sql);
        if (insert.find() && TENANT_TABLES.contains(insert.group(1).toLowerCase(Locale.ROOT))) {
            return new RewriteResult(injectInsert(sql), countMarks(sql));
        }
        Matcher update = UPDATE.matcher(sql);
        Matcher delete = DELETE.matcher(sql);
        Matcher from = FROM.matcher(sql);
        String table = null;
        if (update.find()) {
            table = update.group(1);
        } else if (delete.find()) {
            table = delete.group(1);
        } else if (from.find()) {
            table = from.group(1);
        }
        if (table == null || !TENANT_TABLES.contains(table.toLowerCase(Locale.ROOT))) {
            return new RewriteResult(sql, -1);
        }
        rejectUnsafe(sql);
        return injectPredicate(sql);
    }

    private static int countMarks(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    private static void rejectUnsafe(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        if (Pattern.compile("\\bjoin\\b", Pattern.CASE_INSENSITIVE).matcher(sql).find()
                || Pattern.compile("\\(\\s*select\\b", Pattern.CASE_INSENSITIVE).matcher(sql).find()
                || lower.contains("exists(")) {
            throw new IllegalStateException(
                    "跨表/子查询 SQL 必须手写 tenant_id 条件，禁止自动注入: " + compact(sql));
        }
    }

    private static String injectInsert(String sql) {
        Matcher valuesMatcher = Pattern.compile("\\bvalues\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(sql);
        int valuesIdx = valuesMatcher.find() ? valuesMatcher.start() : -1;
        if (valuesIdx < 0) {
            throw new IllegalStateException("INSERT 缺少 VALUES，无法自动注入租户: " + compact(sql));
        }
        int intoIdx = sql.toLowerCase(java.util.Locale.ROOT).indexOf("into");
        int columnStart = sql.indexOf('(', intoIdx);
        int columnEnd = sql.indexOf(')', columnStart);
        int valueEnd = matchingParen(sql, sql.indexOf('(', valuesMatcher.start() + 6));
        if (columnStart < 0 || columnEnd < 0 || valueEnd < 0) {
            throw new IllegalStateException("INSERT 结构无法识别，请手写 tenant_id: " + compact(sql));
        }
        String tail = sql.substring(valueEnd);
        if (tail.contains("?")) {
            throw new IllegalStateException("INSERT 尾部含占位符，需手写 tenant_id: " + compact(sql));
        }
        return sql.substring(0, columnEnd) + ", tenant_id" + sql.substring(columnEnd, valueEnd)
                + ", ?" + sql.substring(valueEnd);
    }

    private static int matchingParen(String sql, int openIdx) {
        if (openIdx < 0) {
            return -1;
        }
        int depth = 0;
        for (int i = openIdx; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static RewriteResult injectPredicate(String sql) {
        Matcher tail = TAIL.matcher(sql);
        int insertAt = tail.find() ? tail.start() : sql.length();
        boolean hasWhere = Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE)
                .matcher(sql).find();
        String predicate = hasWhere ? " AND tenant_id = ?" : " WHERE tenant_id = ?";
        String rewritten = sql.substring(0, insertAt).stripTrailing() + predicate + " "
                + sql.substring(insertAt);
        int argIndex = countMarks(sql.substring(0, insertAt));
        return new RewriteResult(rewritten, argIndex);
    }

    private static String compact(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 160) + "...";
    }
}
