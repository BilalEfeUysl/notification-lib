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
package io.github.bilalefeuysl.notification.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Veritabanina yazilmis, kimligi ve zamani belli bir bildirim.
 *
 * @param id               birincil anahtar
 * @param classification   bildirimin BASLIGI - VARSAYILAN metin (zorunlu)
 * @param message          bildirimin icerigi - VARSAYILAN metin (zorunlu)
 * @param classificationEn opsiyonel Ingilizce baslik; yoksa null (bkz. {@link #resolvedClassification})
 * @param messageEn        opsiyonel Ingilizce icerik; yoksa null
 * @param type             serbest metin gorsel tip (orn. "WARNING", "BAKIM_GEREKLI")
 * @param priority         oncelik (LOW/NORMAL/HIGH)
 * @param sourceDeviceId   opsiyonel kaynak cihaz kimligi
 * @param createdAt        sunucuda atanan olusturma zamani
 * @param visible          false ise soft delete edilmis demektir
 * @param saved            kullanici bu bildirimi kaydetmis mi (read/hidden'in aksine geri alinabilir)
 * @param metadata         serbest ek veri (JSONB olarak saklanir)
 */
public record Notification(
        UUID id,
        String classification,
        String message,
        String classificationEn,
        String messageEn,
        String type,
        NotificationPriority priority,
        String sourceDeviceId,
        Instant createdAt,
        boolean visible,
        boolean read,
        boolean saved,
        Map<String, Object> metadata,
        NotificationAudience audience
) {
    public Notification {
        Objects.requireNonNull(id, "id null olamaz");
        Objects.requireNonNull(classification, "classification null olamaz");
        Objects.requireNonNull(message, "message null olamaz");
        Objects.requireNonNull(type, "type null olamaz");
        Objects.requireNonNull(priority, "priority null olamaz");
        Objects.requireNonNull(createdAt, "createdAt null olamaz");
        // Bir dil ya TAM ya HIC: yarim Ingilizce (sadece baslik veya sadece mesaj) olamaz.
        if ((classificationEn == null) != (messageEn == null)) {
            throw new IllegalArgumentException(
                    "classificationEn ve messageEn ya birlikte dolu ya birlikte null olmali");
        }
        metadata = (metadata == null)
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        audience = (audience == null) ? new NotificationAudience.Everyone() : audience;
    }

    /**
     * Ingilizce metni olmayan bildirim - eski cagri imzasiyla uyumluluk icin
     * (classificationEn/messageEn = null).
     */
    public Notification(UUID id, String classification, String message, String type,
                        NotificationPriority priority, String sourceDeviceId, Instant createdAt,
                        boolean visible, boolean read, boolean saved, Map<String, Object> metadata,
                        NotificationAudience audience) {
        this(id, classification, message, null, null, type, priority, sourceDeviceId, createdAt,
                visible, read, saved, metadata, audience);
    }

    /**
     * Verilen dile gore gosterilecek baslik: dil "en" ve Ingilizce baslik varsa onu,
     * aksi halde varsayilan basligi doner. (Frontend de ayni mantigi uygular - bu
     * yardimci sunucu tarafinda gerekirse diye.)
     */
    public String resolvedClassification(String language) {
        return ("en".equals(language) && classificationEn != null) ? classificationEn : classification;
    }

    /** {@link #resolvedClassification} ile ayni mantik, mesaj icin. */
    public String resolvedMessage(String language) {
        return ("en".equals(language) && messageEn != null) ? messageEn : message;
    }
}