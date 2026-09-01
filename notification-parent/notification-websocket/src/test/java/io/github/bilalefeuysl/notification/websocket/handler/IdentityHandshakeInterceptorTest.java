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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * REST tarafinda ayni desen (identityResolver'in IllegalStateException
 * firlatmasi) 500 -> 400'e cevrilmisti (bkz. CLAUDE.md, "Ek duzeltme").
 * WebSocket'te HTTP status/@ControllerAdvice kavrami yok; bu test, handshake
 * interceptor'in ayni durumu ZATEN dogru karsiladigini (baglantiyi 401
 * Unauthorized ile reddederek) dogrular - REST'teki gibi ayrica bir
 * duzeltme GEREKMEDIGINI kanitlar.
 */
class IdentityHandshakeInterceptorTest {

    private final ServerHttpRequest request = mock(ServerHttpRequest.class);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

    @Test
    void targetingDisabledAcceptsHandshakeWithoutCallingResolver() {
        NotificationIdentityResolver resolver = mock(NotificationIdentityResolver.class);
        IdentityHandshakeInterceptor interceptor = new IdentityHandshakeInterceptor(false, resolver);

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertThat(accepted).isTrue();
        verifyNoInteractions(resolver);
    }

    @Test
    void resolvedIdentityIsStoredInAttributesAndHandshakeAccepted() {
        NotificationIdentity identity = new NotificationIdentity("user1", Set.of());
        NotificationIdentityResolver resolver = req -> identity;
        IdentityHandshakeInterceptor interceptor = new IdentityHandshakeInterceptor(true, resolver);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(IdentityHandshakeInterceptor.IDENTITY_ATTRIBUTE)).isEqualTo(identity);
    }

    @Test
    void missingIdentityRejectsHandshakeWithUnauthorized() {
        NotificationIdentityResolver resolver = req -> {
            throw new IllegalStateException("userId ne header'da ne query param'da");
        };
        IdentityHandshakeInterceptor interceptor = new IdentityHandshakeInterceptor(true, resolver);

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
