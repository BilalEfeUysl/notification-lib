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
package io.github.bilalefeuysl.notification.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bilalefeuysl.notification.websocket.session.SessionRegistry;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Bir tarayici WebSocket baglantisi actiginda/kapattiginda/mesaj
 * gonderdiginde Spring tarafindan cagrilan sinif.
 */
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;
    private static final String PONG_MESSAGE = "{\"event\":\"PONG\"}";

    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public NotificationWebSocketHandler(SessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Oturuma, o oturum icin olusturulan {@link ConcurrentWebSocketSessionDecorator}'i
     * baglayan attribute anahtari. handleTextMessage'in PONG cevabini HAM session
     * uzerinden DEGIL bu sarmalayici uzerinden gondermesi icin gerekli - bkz.
     * {@link #handleTextMessage}.
     */
    private static final String SAFE_SESSION_ATTRIBUTE = "notificationSafeSession";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Ham session yerine, es zamanli yazima karsi guvenli sarmalayiciyi kaydediyoruz.
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        // PONG cevabinin de ayni sarmalayicidan gecmesi icin sakliyoruz.
        session.getAttributes().put(SAFE_SESSION_ATTRIBUTE, safeSession);

        // Hedefleme aciksa, IdentityHandshakeInterceptor'in handshake sirasinda
        // attributes'e koydugu kimligi buradan okuyoruz. Hedefleme kapaliyken
        // bu attribute hic yok, identity null kalir.
        NotificationIdentity identity =
                (NotificationIdentity) session.getAttributes().get(IdentityHandshakeInterceptor.IDENTITY_ATTRIBUTE);

        sessionRegistry.add(safeSession, identity);
        log.debug("Yeni WebSocket baglantisi: id={}, toplam={}", session.getId(), sessionRegistry.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.removeById(session.getId());
        log.debug("WebSocket baglantisi kapandi: id={}, sebep={}, toplam={}",
                session.getId(), status, sessionRegistry.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport hatasi, oturum kaldiriliyor: id={}", session.getId(), exception);
        sessionRegistry.removeById(session.getId());
    }

    /**
     * ONEMLI: PONG cevabi HAM {@code session} uzerinden gonderilmemeli.
     * Yayin (LocalBroadcaster) ayni baglantiya SessionRegistry'deki
     * {@link ConcurrentWebSocketSessionDecorator} uzerinden yazar. PONG'u ham
     * session'a yazmak, ayni TCP baglantisina birbirinden habersiz IKI yazma
     * yolu acar; istemcinin PING'i ile bir yayin ayni ana denk geldiginde
     * Tomcat/Jetty "The remote endpoint was in state [TEXT_PARTIAL_WRITING]"
     * hatasi firlatir ve baglanti kopar. Sarmalayiciyi kullanmak butun
     * yazmalari tek bir kilit/kuyruk uzerinden sirayla gecirir.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            JsonNode node = objectMapper.readTree(payload);
            String event = node.path("event").asText("");
            if ("PING".equals(event)) {
                Object safeSession = session.getAttributes().get(SAFE_SESSION_ATTRIBUTE);
                WebSocketSession target =
                        (safeSession instanceof WebSocketSession ws) ? ws : session;
                target.sendMessage(new TextMessage(PONG_MESSAGE));
            } else {
                log.debug("Bilinmeyen mesaj, yok sayildi: {}", payload);
            }
        } catch (Exception ex) {
            log.warn("Istemciden gelen mesaj islenemedi: {}", payload, ex);
        }
    }
}