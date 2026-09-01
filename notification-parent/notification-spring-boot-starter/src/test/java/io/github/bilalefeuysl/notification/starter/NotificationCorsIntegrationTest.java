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
 * K1 (denetim listesi) regresyon testi: capraz-origin (CORS) erisimi artik
 * SABIT "*" degil, notification.cors.allowed-origins ayarindan geliyor.
 * Bu sinif ayarin DOLU oldugu senaryoyu dogrular:
 * - listedeki origin'e izin verilir (Access-Control-Allow-Origin doner),
 * - listede olmayan origin reddedilir (403).
 * Ayarin BOS oldugu (varsayilan) senaryo NotificationStarterIntegrationTest
 * icindeki crossOriginRequestsAreBlockedByDefault testinde.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = NotificationCorsIntegrationTest.TestApp.class,
        properties = "notification.cors.allowed-origins=http://allowed.example")
@Testcontainers
class NotificationCorsIntegrationTest {

    static {
        // JDK HttpURLConnection (TestRestTemplate'in varsayilani) "Origin"i
        // kisitli baslik sayip siler; bu olmadan istek sunucuya CORS istegi
        // olarak hic ulasmaz.
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void allowedOriginGetsCorsHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, "http://allowed.example");
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://allowed.example");
    }

    @Test
    void disallowedOriginIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, "http://evil.example");
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
