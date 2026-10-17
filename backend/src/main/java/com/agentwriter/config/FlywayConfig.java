package com.agentwriter.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 数据库迁移配置。
 *
 * <p>Spring Boot 4 已移除 Flyway 的自动配置（spring-boot-autoconfigure 中不再包含
 * FlywayAutoConfiguration），因此这里手动注册 Flyway Bean 并在容器初始化阶段执行迁移。
 * 迁移脚本统一放在 {@code classpath:db/migration}（V1 全量建库 + V2~V6 增量变更）。
 *
 * <p>采用 {@code baselineOnMigrate + baselineVersion=0}：对于已由 Hibernate 建过表的
 * 存量库（无 schema history 表），Flyway 会以版本 0 作为基线，然后依次执行 V1~V6；
 * 全部迁移脚本均为幂等写法（IF NOT EXISTS / OR REPLACE），可安全重复执行。
 */
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }
}
