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
package io.github.bilalefeuysl.notification.rest.dto;

import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.rest.error.InvalidNotificationRequestException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * sort=priority modunda kullanilan, istemci icin OPAK sayfalama imleci.
 * Tek bir Instant ("before") yetmez - ayni created_at farkli oncelikte
 * olabilir, bu yuzden ucu de (priority, createdAt, id) tasiyan bu ayri
 * imlec kullanilir. Istemci sadece "encode edilmis metni geri gonder"
 * bilir, icerigini hic yorumlamaz - tipki normal "before" gibi.
 */
public record PriorityCursor(NotificationPriority priority, Instant createdAt, UUID id) {

    public static String encode(Notification last) {
        String raw = last.priority().name() + "|" + last.createdAt().toString() + "|" + last.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** cursor null/bos ise ilk sayfayi temsil eden (hepsi null) bir PriorityCursor doner. */
    public static PriorityCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new PriorityCursor(null, null, null);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("beklenen parca sayisi 3 degil");
            }
            return new PriorityCursor(NotificationPriority.valueOf(parts[0]), Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (RuntimeException ex) {
            throw new InvalidNotificationRequestException("priorityCursor gecersiz");
        }
    }
}
