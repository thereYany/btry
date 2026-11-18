package com.wherelee.base;

import com.wherelee.base.app.alert.AlertProperties;
import com.wherelee.base.bootstrap.KernelSeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通用业务基座演示宿主。
 *
 * <p>只装配内核能力（platform starter + identity + payment）与一个最小的管理端，
 * 用来证明「内核可以独立启动、独立测试、独立部署」。任何垂直业务都不应出现在这里。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.wherelee.base.app",
        "com.wherelee.base.bootstrap",
        "com.wherelee.base.modules.identity",
        "com.wherelee.base.modules.payment"
})
@EnableConfigurationProperties({KernelSeedProperties.class, AlertProperties.class})
@EnableScheduling
public class BtryApplication {

    public static void main(String[] args) {
        SpringApplication.run(BtryApplication.class, args);
    }
}
