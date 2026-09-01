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
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationAudience;
import io.github.bilalefeuysl.notification.core.model.NotificationCommand;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 25 Agustos'ta cozulen SPECIFIC_USER hedefleme bug'inin (bkz. CLAUDE.md
 * bolum 5) gercek Testcontainers ortaminda otomatik regresyon testi.
 * NotificationStarterIntegrationTest hedefleme KAPALI calisir; bu sinif
 * hedefleme ACIKKEN iki farkli kimligin (user1/user2) birbirinin
 * bildirimini GORMEDIGINI dogrular - simdiye kadar bu senaryo sadece elle
 * veya mock seviyesinde (NotificationControllerTargetingTest) test edilmisti.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = NotificationStarterTargetingIntegrationTest.TestApp.class,
        properties = "notification.targeting.enabled=true")
@Testcontainers
class NotificationStarterTargetingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    NotificationService notificationService;

    @Test
    void usersOnlySeeNotificationsTargetedAtThem() {
        notificationService.publish(NotificationCommand.builder()
                .classification("Sadece user1'e")
                .message("icerik")
                .audience(new NotificationAudience.SpecificUser("user1"))
                .build());

        ResponseEntity<String> asUser1 = listAs("user1");
        assertThat(asUser1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asUser1.getBody()).contains("Sadece user1'e");

        ResponseEntity<String> asUser2 = listAs("user2");
        assertThat(asUser2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asUser2.getBody()).doesNotContain("Sadece user1'e");
    }

    @Test
    void savedNotificationIsPerUser() {
        // "saved" ucu PATCH kullaniyor; TestRestTemplate'in varsayilan
        // istemcisi (JDK HttpURLConnection tabanli) PATCH DESTEKLEMIYOR -
        // ekstra bir HTTP istemci bagimliligi eklemek yerine (kurumsal
        // ag/sertifika kisitlamalari yuzunden indirilemeyebiliyor, bkz.
        // CLAUDE.md), kaydetme adimi dogrudan servis uzerinden yapiliyor.
        // Asil test edilen sey (okuma tarafinin kisiye ozel filtrelemesi)
        // yine gercek HTTP ucu uzerinden dogrulaniyor.
        Notification published = notificationService.publish(NotificationCommand.builder()
                .classification("Herkese Acik")
                .message("icerik")
                .build());
        notificationService.setSaved(published.id(), true, new NotificationIdentity("user1", null));

        ResponseEntity<String> user1Saved = restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications?saved=true",
                HttpMethod.GET, new HttpEntity<>(headersFor("user1")), String.class);
        assertThat(user1Saved.getBody()).contains("Herkese Acik");

        // user2 AYNI bildirimi gorebiliyor (Everyone) ama KENDI kaydetmemis -
        // kaydedilenler gorunumunde ona hic gorunmemeli (kisiye ozel).
        ResponseEntity<String> user2Saved = restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications?saved=true",
                HttpMethod.GET, new HttpEntity<>(headersFor("user2")), String.class);
        assertThat(user2Saved.getBody()).doesNotContain("Herkese Acik");
    }

    private HttpHeaders headersFor(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        return headers;
    }

    @Test
    void searchOnlyMatchesNotificationsVisibleToUser() {
        notificationService.publish(NotificationCommand.builder()
                .classification("Sadece user1'e Ariza Bildirimi")
                .message("icerik")
                .audience(new NotificationAudience.SpecificUser("user1"))
                .build());

        ResponseEntity<String> asUser1 = restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications?q=ariza",
                HttpMethod.GET, new HttpEntity<>(headersFor("user1")), String.class);
        assertThat(asUser1.getBody()).contains("Ariza Bildirimi");

        // user2'ye HIC HEDEFLENMEMIS, arama sonucunda da GORUNMEMELI.
        ResponseEntity<String> asUser2 = restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications?q=ariza",
                HttpMethod.GET, new HttpEntity<>(headersFor("user2")), String.class);
        assertThat(asUser2.getBody()).doesNotContain("Ariza Bildirimi");
    }

    @Test
    void sortPriorityHedeflemedeDeKisiyeOzelKalir() {
        // user1'e hedefli bir HIGH bildirim, user2'ye hedefli bir HIGH
        // bildirim - sort=priority ile user1 olarak sorgulaninca SADECE
        // kendisine hedeflenen gorunmeli, sirali listede digerinin
        // sizmadigini dogruluyoruz (B11'in hedeflemeyle etkilesimi).
        notificationService.publish(NotificationCommand.builder()
                .classification("User1 Yuksek Oncelik")
                .message("icerik")
                .priority(NotificationPriority.HIGH)
                .audience(new NotificationAudience.SpecificUser("user1"))
                .build());
        notificationService.publish(NotificationCommand.builder()
                .classification("User2 Yuksek Oncelik")
                .message("icerik")
                .priority(NotificationPriority.HIGH)
                .audience(new NotificationAudience.SpecificUser("user2"))
                .build());

        ResponseEntity<String> asUser1 = restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications?sort=priority",
                HttpMethod.GET, new HttpEntity<>(headersFor("user1")), String.class);

        assertThat(asUser1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asUser1.getBody()).contains("User1 Yuksek Oncelik");
        assertThat(asUser1.getBody()).doesNotContain("User2 Yuksek Oncelik");
    }

    @Test
    void missingIdentityHeaderIsRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/notifications", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> listAs(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
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
