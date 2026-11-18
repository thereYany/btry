package com.wherelee.base.modules.platform.tenant;

/**
 * 租户上下文。
 *
 * <p>由鉴权过滤器从 JWT claim 写入，SQL 拦截器从这里取租户值注入查询条件。
 * 业务代码**不得**从请求参数里取租户——那等于把越权入口交给客户端。</p>
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private static final long DEFAULT_TENANT = 0L;

    private TenantContext() {
    }

    public static Long currentOrNull() {
        return CURRENT.get();
    }

    public static long current() {
        Long value = CURRENT.get();
        return value == null ? DEFAULT_TENANT : value;
    }

    public static void set(Long tenantId) {
        if (tenantId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
