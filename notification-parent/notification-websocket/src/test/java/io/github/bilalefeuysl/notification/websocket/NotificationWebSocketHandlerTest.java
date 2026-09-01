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
package io.github.bilalefeuysl.notification.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.model.NotificationType;
import io.github.bilalefeuysl.notification.websocket.broadcast.LocalBroadcaster;
import io.github.bilalefeuysl.notification.websocket.handler.NotificationWebSocketHandler;
import io.github.bilalefeuysl.notification.core.model.NotificationAudience;
import io.github.bilalefeuysl.notification.websocket.session.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = NotificationWebSocketHandlerTest.TestApp.class)
class NotificationWebSocketHandlerTest {

    @LocalServerPort
    int port;

    @Autowired
    SessionRegistry sessionRegistry;

    @Autowired
    LocalBroadcaster broadcaster;

    @Test
    void publishedNotificationReachesConnectedClient() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession clientSession = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.complete(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws/notifications").get(5, TimeUnit.SECONDS);

        awaitSessionRegistered();

        Notification notification = new Notification(
                UUID.randomUUID(), "Test Basligi", "Test icerik",
                "INFO", NotificationPriority.NORMAL, null, Instant.now(), true, false, false, Map.of(),
                new NotificationAudience.Everyone());

        broadcaster.broadcast(notification);

        String json = received.get(5, TimeUnit.SECONDS);
        assertThat(json).contains("NOTIFICATION_CREATED");
        assertThat(json).contains(notification.id().toString());
        assertThat(json).contains("Test Basligi");

        clientSession.close();
    }

    @Test
    void hiddenEventReachesConnectedClient() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession clientSession = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.complete(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws/notifications").get(5, TimeUnit.SECONDS);

        awaitSessionRegistered();

        UUID hiddenId = UUID.randomUUID();
        broadcaster.broadcastHidden(List.of(hiddenId));

        String json = received.get(5, TimeUnit.SECONDS);
        assertThat(json).contains("NOTIFICATION_HIDDEN");
        assertThat(json).contains(hiddenId.toString());

        clientSession.close();
    }

    @Test
    void allHiddenEventReachesConnectedClient() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession clientSession = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.complete(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws/notifications").get(5, TimeUnit.SECONDS);

        awaitSessionRegistered();

        broadcaster.broadcastAllHidden();

        String json = received.get(5, TimeUnit.SECONDS);
        assertThat(json).contains("NOTIFICATION_ALL_HIDDEN");

        clientSession.close();
    }

    @Test
    void readEventReachesConnectedClient() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession clientSession = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.complete(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws/notifications").get(5, TimeUnit.SECONDS);

        awaitSessionRegistered();

        UUID readId = UUID.randomUUID();
        broadcaster.broadcastRead(List.of(readId));

        String json = received.get(5, TimeUnit.SECONDS);
        assertThat(json).contains("NOTIFICATION_READ");
        assertThat(json).contains(readId.toString());

        clientSession.close();
    }

    private void awaitSessionRegistered() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (sessionRegistry.size() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    @SpringBootApplication
    @EnableWebSocket
    static class TestApp {

        @Bean
        SessionRegistry sessionRegistry() {
            return new SessionRegistry();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

        @Bean
        NotificationWebSocketHandler notificationWebSocketHandler(SessionRegistry registry, ObjectMapper mapper) {
            return new NotificationWebSocketHandler(registry, mapper);
        }

        @Bean
        LocalBroadcaster localBroadcaster(SessionRegistry registry, ObjectMapper mapper) {
            return new LocalBroadcaster(registry, mapper);
        }

        @Bean
        WebSocketConfigurer webSocketConfigurer(NotificationWebSocketHandler handler) {
            return registry -> registry.addHandler(handler, "/ws/notifications").setAllowedOrigins("*");
        }
    }
}