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
package io.github.bilalefeuysl.notification.websocket.session;

import io.github.bilalefeuysl.notification.core.model.NotificationAudience;
import io.github.bilalefeuysl.notification.core.model.NotificationAudienceMatcher;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O an baglantida olan WebSocket oturumlarinin kaydi. Hedefleme (targeting)
 * acikken, her oturumun HANGI KULLANICIYA ait oldugu (identity) da burada
 * saklanir - boylece bir bildirim yayinlanirken sadece ilgili oturumlara
 * gonderilebilir. Hedefleme kapaliyken identity hep null'dir, bu da
 * "herkese gonder" davranisiyla ayni sonucu verir.
 */
public class SessionRegistry {

    private record Entry(WebSocketSession session, NotificationIdentity identity) {
    }

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    /** Hedefleme kapaliyken kullanilir - kimlik bilgisi yok. */
    public void add(WebSocketSession session) {
        add(session, null);
    }

    /** Hedefleme acikken kullanilir - oturum, cozulen kimlikle birlikte kaydedilir. */
    public void add(WebSocketSession session, NotificationIdentity identity) {
        sessions.put(session.getId(), new Entry(session, identity));
    }

    public void removeById(String sessionId) {
        sessions.remove(sessionId);
    }

    public Collection<WebSocketSession> all() {
        List<WebSocketSession> result = new ArrayList<>(sessions.size());
        for (Entry entry : sessions.values()) {
            result.add(entry.session());
        }
        return result;
    }

    /**
     * Verilen audience'a uyan oturumlari doner. Bir oturumun identity'si
     * yoksa (hedefleme hic calismiyorsa) HER ZAMAN eslesir - bu, hedefleme
     * kapaliyken eski "herkese yayinla" davranisini korur.
     */
    public Collection<WebSocketSession> matching(NotificationAudience audience) {
        List<WebSocketSession> result = new ArrayList<>();
        for (Entry entry : sessions.values()) {
            if (entry.identity() == null || NotificationAudienceMatcher.matches(audience, entry.identity())) {
                result.add(entry.session());
            }
        }
        return result;
    }

        /**
     * Verilen userId'ye ait oturumlari doner. Bir oturumun identity'si yoksa
     * (hedefleme kapaliysa) HER ZAMAN eslesir - matching() ile ayni mantik.
     */
    public Collection<WebSocketSession> matchingUser(String userId) {
        List<WebSocketSession> result = new ArrayList<>();
        for (Entry entry : sessions.values()) {
            if (entry.identity() == null || entry.identity().userId().equals(userId)) {
                result.add(entry.session());
            }
        }
        return result;
    }

    public int size() {
        return sessions.size();
    }
}