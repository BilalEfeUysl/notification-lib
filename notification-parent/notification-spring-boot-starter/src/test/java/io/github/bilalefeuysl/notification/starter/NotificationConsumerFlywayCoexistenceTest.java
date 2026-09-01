/*
 * Copyright 2026 Bilal Efe Uysal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bilalefeuysl.notification.starter;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kutuphane, Spring Boot'un Flyway'ini "ele gecirip" tuketicinin KENDI
 * migration'larini sessizce durdurmamali.
 * <p>
 * Bu test, tuketicinin kendi Flyway migration'ini {@code spring.flyway.locations}
 * ile bildirdigi senaryoyu kurar. Beklenen:
 *   - {@code OnNoConsumerFlywayMigrations} kosulu bunu gorup {@code notificationFlyway}
 *     "bastirici" bean'ini KAYDETMEZ,
 *   - Boot kendi Flyway'ini kurar ve tuketicinin migration'ini calistirir
 *     ({@code consumer_widget} tablosu olusur),
 *   - kutuphanenin kendi semasi ({@code notifications} tablosu) yine hazirdir -
 *     {@code NotificationSchemaInitializer} onu bagimsiz olarak, ayri gecmis
 *     tablolariyla kurar.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = NotificationConsumerFlywayCoexistenceTest.TestApp.class,
        properties = {
                "spring.flyway.locations=classpath:db/consumer-migration",
                "notification.websocket.enabled=false",
                "notification.rest.enabled=false"
        })
@Testcontainers
class NotificationConsumerFlywayCoexistenceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void doesNotRegisterTheSuppressorFlywayBeanWhenConsumerHasItsOwnMigrations() {
        assertThat(applicationContext.getBeanNamesForType(Flyway.class))
                .doesNotContain("notificationFlyway");
    }

    @Test
    void consumerOwnFlywayMigrationRuns() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'consumer_widget'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void librarySchemaIsStillPrepared() {
        Integer notifications = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'notifications'",
                Integer.class);
        assertThat(notifications).isEqualTo(1);

        // Kutuphanenin kendi gecmis tablosu, Boot'unkinden (flyway_schema_history) ayri.
        Integer libraryHistory = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'notification_schema_history'",
                Integer.class);
        assertThat(libraryHistory).isEqualTo(1);
    }

    @SpringBootApplication
    static class TestApp {

        @Bean
        DataSource dataSource() {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(postgres.getJdbcUrl());
            ds.setUsername(postgres.getUsername());
            ds.setPassword(postgres.getPassword());
            return ds;
        }
    }
}
