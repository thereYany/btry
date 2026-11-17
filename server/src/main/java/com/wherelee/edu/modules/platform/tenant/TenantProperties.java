package com.wherelee.edu.modules.platform.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租户隔离配置。
 *
 * @param enforce true = SQL 未带租户条件时直接拒绝（生产必须为 true）
 */
@ConfigurationProperties(prefix = "app.tenant")
public record TenantProperties(boolean enforce, long defaultTenantId) {
}
