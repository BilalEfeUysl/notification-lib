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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bilalefeuysl.notification.core.broadcast.NotificationBroadcaster;
import io.github.bilalefeuysl.notification.core.config.NotificationProperties;
import io.github.bilalefeuysl.notification.websocket.broadcast.LocalBroadcaster;
import io.github.bilalefeuysl.notification.websocket.handler.IdentityHandshakeInterceptor;
import io.github.bilalefeuysl.notification.websocket.handler.NotificationWebSocketHandler;
import io.github.bilalefeuysl.notification.websocket.identity.HeaderNotificationIdentityResolver;
import io.github.bilalefeuysl.notification.websocket.identity.NotificationIdentityResolver;
import io.github.bilalefeuysl.notification.websocket.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;

import java.util.List;

/**
 * Sadece notification.websocket.enabled=false DEGILSE aktif olur (varsayilan: acik).
 * NotificationAutoConfiguration tarafindan @Import ile devreye alinir.
 */
@Configuration
@ConditionalOnProperty(prefix = "notification.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(WebSocketHandler.class)
@EnableWebSocket
public class NotificationWebSocketAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketAutoConfiguration.class);

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(NotificationWebSocketHandler.class)
    public NotificationWebSocketHandler notificationWebSocketHandler(
            SessionRegistry sessionRegistry,
            @Qualifier("notificationObjectMapper") ObjectMapper notificationObjectMapper) {
        return new NotificationWebSocketHandler(sessionRegistry, notificationObjectMapper);
    }

    /** Kullanan uygulama kendi NotificationBroadcaster'ini (orn. Redis tabanli) tanimlarsa, bizimki devre disi kalir. */
    @Bean
    @ConditionalOnMissingBean(NotificationBroadcaster.class)
    public NotificationBroadcaster localBroadcaster(
            SessionRegistry sessionRegistry,
            @Qualifier("notificationObjectMapper") ObjectMapper notificationObjectMapper) {
        return new LocalBroadcaster(sessionRegistry, notificationObjectMapper);
    }

    /**
     * Varsayilan kimlik cozucu - header'lardan okur. Kullanan uygulama kendi
     * implementasyonunu (orn. Spring Security'den okuyan) tanimlarsa bizimki
     * devre disi kalir. targeting kapaliyken bu bean hic cagrilmaz, sadece
     * durur.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationIdentityResolver.class)
    public NotificationIdentityResolver notificationIdentityResolver() {
        return new HeaderNotificationIdentityResolver();
    }

    @Bean
    public IdentityHandshakeInterceptor identityHandshakeInterceptor(NotificationProperties properties,
                                                                        NotificationIdentityResolver identityResolver) {
        return new IdentityHandshakeInterceptor(properties.getTargeting().isEnabled(), identityResolver);
    }

    @Bean
    public WebSocketConfigurer notificationWebSocketConfigurer(NotificationWebSocketHandler handler,
                                                                  NotificationProperties properties,
                                                                  IdentityHandshakeInterceptor identityHandshakeInterceptor) {
        String path = properties.getWebsocket().getPath();
        List<String> origins = properties.getCors().getAllowedOrigins();
        return registry -> {
            WebSocketHandlerRegistration registration = registry.addHandler(handler, path)
                    .addInterceptors(identityHandshakeInterceptor);
            if (origins == null || origins.isEmpty()) {
                // setAllowedOrigins hic cagrilmaz -> Spring'in varsayilani: yalnizca ayni-origin.
                log.info("notification WebSocket: capraz-origin baglantilar engelli (yalnizca ayni-origin). "
                        + "Frontend farkli bir origin'deyse notification.cors.allowed-origins ayarina ekleyin.");
            } else {
                registration.setAllowedOrigins(origins.toArray(new String[0]));
                log.info("notification WebSocket: su origin'lere izin veriliyor -> {}", origins);
            }
        };
    }
}