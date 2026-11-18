package com.wherelee.base.modules.identity.rbac;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wherelee.base.modules.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * 用户权限解析（带缓存）。
 *
 * <p>缓存 key 含租户，避免多租户串号；角色变更或用户被禁用时调用 {@link #invalidate}。
 * TTL 只是兜底，正常路径靠显式失效。</p>
 */
@Service
public class PermissionService {

    private final RbacRepository rbacRepository;
    private final Cache<String, Set<String>> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();

    public PermissionService(RbacRepository rbacRepository) {
        this.rbacRepository = rbacRepository;
    }

    public Set<String> permissionsOf(Long userId) {
        Long tenantId = TenantContext.current();
        return cache.get(cacheKey(tenantId, userId),
                key -> Set.copyOf(rbacRepository.findPermissionCodesByUser(userId)));
    }

    public boolean has(Long userId, String permissionCode) {
        return permissionOf(userId, permissionCode);
    }

    public boolean hasAny(Long userId, String... permissionCodes) {
        Set<String> owned = permissionsOf(userId);
        for (String code : permissionCodes) {
            if (owned.contains(code)) {
                return true;
            }
        }
        return false;
    }

    public void invalidate(Long userId) {
        cache.invalidate(cacheKey(TenantContext.current(), userId));
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private boolean permissionOf(Long userId, String code) {
        return permissionsOf(userId).contains(code);
    }

    private static String cacheKey(Long tenantId, Long userId) {
        return tenantId + ":" + userId;
    }
}
