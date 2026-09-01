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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bilalefeuysl.notification.core.service.NotificationService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spring.flyway.enabled=false gibi bir workaround'a KASITLI olarak yer verilmiyor:
 * NotificationAutoConfiguration'daki notificationFlyway bean'i sayesinde Spring Boot'un
 * kendi FlywayAutoConfiguration'i @ConditionalOnMissingBean(Flyway.class) korumasi ile
 * kendiliginden devre disi kaliyor. Bu testin workaround'suz gecmesi, o mekanizmanin
 * dogru calistiginin kanitidir.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = NotificationStarterIntegrationTest.TestApp.class)
@Testcontainers
class NotificationStarterIntegrationTest {

    static {
        // crossOriginRequestsAreBlockedByDefault'un anlamli olmasi icin: JDK
        // HttpURLConnection "Origin"i kisitli baslik sayip siler, bu olmadan
        // test yanlis sebeple gecerdi.
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    NotificationService notificationService;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void doesNotRegisterASecondDataSourceWhenAppProvidesOne() {
        // notification.datasource.url bos -> kutuphane kendi DataSource bean'ini
        // OLUSTURMAMALI, sadece uygulamanin "dataSource" bean'ini kullanmali.
        // Iki DataSource birakmak @Autowired DataSource'u belirsiz yapar ve
        // Spring Boot'un tek-aday'a bagli parcalarini (JdbcTemplate vb.) susturur.
        assertThat(applicationContext.getBeanNamesForType(DataSource.class))
                .containsExactly("dataSource");
    }

    @Test
    void doesNotHijackTheApplicationObjectMapper() {
        // NotificationAutoConfiguration @AutoConfigureAfter(JacksonAutoConfiguration.class)
        // oldugu icin Boot'un @Primary ObjectMapper'i once kaydolur, bizim
        // notificationObjectMapper ikincil kalir. Primary mapper (MVC / @RequestBody
        // bunu kullanir) Boot'un yapilandirdigi mapper olmali: FAIL_ON_UNKNOWN_PROPERTIES
        // KAPALI. Bizim ciplak "new ObjectMapper()" bean'imizde bu ozellik ACIK
        // (Jackson varsayilani) olurdu - yani @AutoConfigureAfter kalkarsa bu test kirilir.
        ObjectMapper primary = applicationContext.getBean(ObjectMapper.class);
        assertThat(primary.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(applicationContext.getBeanNamesForType(ObjectMapper.class))
                .contains("jacksonObjectMapper", "notificationObjectMapper");
    }

    @Test
    void restEndpointWorksOutOfTheBox() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/api/notifications", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"items\"").contains("\"hasMore\":false");
    }

    @Test
    void crossOriginRequestsAreBlockedByDefault() {
        // notification.cors.allowed-origins ayarlanmadi -> capraz-origin bir
        // tarayici istegine CORS izin basligi DONMEMELI (tarayici cevabi
        // JavaScript'e okutmaz).
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, "http://evil.example");
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/notifications",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    void webSocketEndpointAcceptsConnections() throws Exception {
        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client
                .execute(new TextWebSocketHandler() {}, "ws://localhost:" + port + "/ws/notifications")
                .get(5, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();
        session.close();
    }

    @SpringBootApplication
    static class TestApp {

        // notification.datasource.* KASITLI olarak bos: starter'in bu DataSource'u
        // otomatik bulup kullandigini dogruluyoruz.
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