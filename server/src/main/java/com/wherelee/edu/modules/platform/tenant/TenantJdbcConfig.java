package com.wherelee.edu.modules.platform.tenant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 用手持租户拦截的 JdbcTemplate 替换默认实现。
 */
@Configuration
public class TenantJdbcConfig {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource, TenantProperties properties) {
        return new TenantAwareJdbcTemplate(dataSource, properties.enforce());
    }
}
