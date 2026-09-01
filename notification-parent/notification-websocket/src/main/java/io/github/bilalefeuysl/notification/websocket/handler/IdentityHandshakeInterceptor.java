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

import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import io.github.bilalefeuysl.notification.websocket.identity.NotificationIdentityResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Baglanti kurulmadan HEMEN ONCE (handshake sirasinda) calisir. Hedefleme
 * aciksa kimligi cozup, sonucu attributes Map'ine koyar - bu Map, baglanti
 * kurulduktan sonra WebSocketSession.getAttributes() ile okunabilir hale
 * gelir (NotificationWebSocketHandler.afterConnectionEstablished bunu okur).
 * Hedefleme kapaliyken bu interceptor hicbir sey yapmaz, sadece true doner.
 */
public class IdentityHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdentityHandshakeInterceptor.class);

    /** NotificationWebSocketHandler'in session attribute'undan okuyacagi anahtar. */
    public static final String IDENTITY_ATTRIBUTE = "notificationIdentity";

    private final boolean targetingEnabled;
    private final NotificationIdentityResolver identityResolver;

    public IdentityHandshakeInterceptor(boolean targetingEnabled, NotificationIdentityResolver identityResolver) {
        this.targetingEnabled = targetingEnabled;
        this.identityResolver = identityResolver;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!targetingEnabled) {
            return true;
        }
        try {
            NotificationIdentity identity = identityResolver.resolve(request);
            attributes.put(IDENTITY_ATTRIBUTE, identity);
            return true;
        } catch (Exception ex) {
            log.warn("WebSocket baglantisi reddedildi, kimlik cozulemedi: {}", ex.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // gerekmiyor
    }
}