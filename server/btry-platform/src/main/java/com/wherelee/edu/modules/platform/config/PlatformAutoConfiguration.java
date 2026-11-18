package com.wherelee.edu.modules.platform.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 平台内核自动配置（starter 入口）。
 *
 * <p>扫描并注册 platform 包内的全部内核组件（租户、追踪、告警、存储、AOP、审计等），
 * 业务应用只需依赖 btry-platform 并在自己的包下放业务组件，不必再扫描 platform 包。</p>
 */
@AutoConfiguration
@ComponentScan(
        basePackages = "com.wherelee.edu.modules.platform",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = PlatformAutoConfiguration.class))
public class PlatformAutoConfiguration {
}
