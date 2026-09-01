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
package io.github.bilalefeuysl.notification.websocket.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bilalefeuysl.notification.core.broadcast.NotificationBroadcaster;
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import io.github.bilalefeuysl.notification.websocket.event.NotificationAllHiddenEvent;
import io.github.bilalefeuysl.notification.websocket.event.NotificationCreatedEvent;
import io.github.bilalefeuysl.notification.websocket.event.NotificationHiddenEvent;
import io.github.bilalefeuysl.notification.websocket.event.NotificationReadEvent;
import io.github.bilalefeuysl.notification.websocket.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class LocalBroadcaster implements NotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LocalBroadcaster.class);

    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public LocalBroadcaster(SessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void broadcast(Notification notification) {
        // Hedefleme kapaliyken (ya da oturumun kimligi bilinmiyorsa) matching()
        // zaten herkesi dondurur - bkz. SessionRegistry.matching().
        Collection<WebSocketSession> targets = sessionRegistry.matching(notification.audience());
        sendTo(targets, NotificationCreatedEvent.of(notification), "yeni bildirim (id=" + notification.id() + ")");
    }

    @Override
    public void broadcastHidden(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        sendTo(sessionRegistry.all(), NotificationHiddenEvent.of(ids), "gizleme olayi (" + ids.size() + " kayit)");
    }

    @Override
    public void broadcastAllHidden() {
        sendTo(sessionRegistry.all(), NotificationAllHiddenEvent.instance(), "tumunu gizleme olayi");
    }

    @Override
    public void broadcastRead(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        sendTo(sessionRegistry.all(), NotificationReadEvent.of(ids), "okundu olayi (" + ids.size() + " kayit)");
    }

        @Override
    public void broadcastHiddenForUser(List<UUID> ids, NotificationIdentity identity) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        sendTo(sessionRegistry.matchingUser(identity.userId()), NotificationHiddenEvent.of(ids),
                "gizleme olayi, kullanici=" + identity.userId());
    }

    @Override
    public void broadcastAllHiddenForUser(NotificationIdentity identity) {
        sendTo(sessionRegistry.matchingUser(identity.userId()), NotificationAllHiddenEvent.instance(),
                "tumunu gizleme olayi, kullanici=" + identity.userId());
    }

    @Override
    public void broadcastReadForUser(List<UUID> ids, NotificationIdentity identity) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        sendTo(sessionRegistry.matchingUser(identity.userId()), NotificationReadEvent.of(ids),
                "okundu olayi, kullanici=" + identity.userId());
    }

    /**
     * Verilen olay nesnesini JSON'a cevirip verilen oturum kumesine yollar.
     * broadcast() sadece hedeflenen oturumlara (sessionRegistry.matching())
     * yollar; broadcastHidden/broadcastAllHidden/broadcastRead ise HALA tum
     * oturumlara (sessionRegistry.all()) yollar - cunku okundu/gizli durumu
     * su an hala paylasilan (kullaniciya ozel olmayan) bir alan.
     */
    private void sendTo(Collection<WebSocketSession> sessions, Object event, String logContext) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            log.error("{} JSON'a cevrilemedi, yayin yapilamadi", logContext, ex);
            return;
        }

        TextMessage message = new TextMessage(json);
        // Yerel, tek is parcacigi tarafindan doldurulan liste - es zamanli
        // koleksiyona gerek yok (her ekleme icin dizi kopyalamasi maliyeti).
        List<String> deadSessionIds = new ArrayList<>();

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                deadSessionIds.add(session.getId());
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException ex) {
                log.warn("Mesaj gonderilemedi, oturum kaldirilacak: id={}", session.getId(), ex);
                deadSessionIds.add(session.getId());
            }
        }

        deadSessionIds.forEach(sessionRegistry::removeById);

        log.debug("{} istemciye yayinlandi: {}", sessions.size(), logContext);
    }
}