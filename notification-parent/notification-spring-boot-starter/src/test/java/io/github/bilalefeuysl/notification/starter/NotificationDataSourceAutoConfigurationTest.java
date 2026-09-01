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
import io.github.bilalefeuysl.notification.core.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationAutoConfiguration'in DataSource kurulum davranisi (O1 + O9):
 * - notification.datasource.url doluysa kendi Hikari havuzunu kurar,
 * - HikariCP classpath'te yoksa (starter'da "optional") ve url ayarlanmissa
 *   acilista net bir hata verir (sessizce uygulama DataSource'una donmez).
 */
class NotificationDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropsConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationProperties.class)
    static class PropsConfig {
    }

    @Test
    void hikariVar_urlDoluysa_kendiHikariDataSourceUnuKurar() {
        runner
                .withUserConfiguration(NotificationAutoConfiguration.HikariNotificationDataSourceConfiguration.class)
                .withPropertyValues(
                        "notification.datasource.url=jdbc:postgresql://localhost:5432/x",
                        "notification.datasource.username=u",
                        "notification.datasource.password=p")
                .run(context -> {
                    assertThat(context).hasBean("notificationDataSource");
                    assertThat(context.getBean("notificationDataSource")).isInstanceOf(HikariDataSource.class);
                });
    }

    @Test
    void hikariVar_urlYoksa_kendiDataSourceBeanIniOlusturmaz() {
        runner
                .withUserConfiguration(NotificationAutoConfiguration.HikariNotificationDataSourceConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean("notificationDataSource"));
    }

    @Test
    void hikariYok_urlDoluysa_acilistaNetHatayaDuser() {
        runner
                .withClassLoader(new FilteredClassLoader(HikariDataSource.class))
                .withUserConfiguration(
                        NotificationAutoConfiguration.HikariNotificationDataSourceConfiguration.class,
                        NotificationAutoConfiguration.MissingHikariConfiguration.class)
                .withPropertyValues("notification.datasource.url=jdbc:postgresql://localhost:5432/x")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("HikariCP");
                });
    }

    @Test
    void hikariYok_urlYoksa_sorunsuzAcilirVeKendiDataSourceUOlusturmaz() {
        runner
                .withClassLoader(new FilteredClassLoader(HikariDataSource.class))
                .withUserConfiguration(
                        NotificationAutoConfiguration.HikariNotificationDataSourceConfiguration.class,
                        NotificationAutoConfiguration.MissingHikariConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("notificationDataSource");
                });
    }
}
