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
package io.github.bilalefeuysl.notification.core.service;

import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationCommand;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.model.NotificationType;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    Notification publish(NotificationCommand command);

    default Notification publish(String classification, String message) {
        return publish(classification, message, NotificationType.INFO);
    }

    default Notification publish(String classification, String message, NotificationType type) {
        return publish(NotificationCommand.builder()
                .classification(classification)
                .message(message)
                .type(type)
                .build());
    }

    /** Kisa yol: baslik, icerik, tip ve metadata ile yayinlar (sourceDeviceId olmadan). */
    default Notification publish(String classification, String message, NotificationType type,
                                  Map<String, Object> metadata) {
        return publish(NotificationCommand.builder()
                .classification(classification)
                .message(message)
                .type(type)
                .metadata(metadata)
                .build());
    }

    /**
     * Zaman imlecli liste sorgusu.
     *
     * @param before null ise en yeniden baslar; doluysa bu andan ESKI kayitlar doner
     * @param limit  donecek en fazla kayit sayisi
     */
    List<Notification> findRecent(Instant before, int limit);

    /** findRecent(before, limit) ile ayni, ek olarak sadece belirtilen onceligi olan kayitlari doner. */
    List<Notification> findRecent(Instant before, int limit, NotificationPriority priority);

    /**
     * Opt-in: once onceliğe (HIGH -> NORMAL -> LOW), sonra created_at DESC'e
     * gore sıralanmis liste. TAMAMEN ayri bir sorgu yolu - findRecent()'i
     * kullanan tuketiciler icin hicbir ek maliyet getirmez.
     * cursorPriority/cursorCreatedAt/cursorId, bir onceki sayfanin son
     * kaydini temsil eder; ilk sayfa icin ucu de null verilir.
     */
    List<Notification> findRecentSortedByPriority(NotificationPriority cursorPriority, Instant cursorCreatedAt,
                                                    UUID cursorId, int limit);

    /** Hedefli modda: findRecentSortedByPriority ile ayni, sadece bu kullaniciya gorunen kayitlarla. */
    default List<Notification> findRecentSortedByPriority(NotificationPriority cursorPriority, Instant cursorCreatedAt,
            UUID cursorId, int limit, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }

    /**
     * Hedefli bildirim modu icin: sadece verilen kimlige uygun kayitlari doner.
     * Varsayilan implementasyon desteklemiyor.
     */
    default List<Notification> findRecent(Instant before, int limit, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }

    default List<Notification> findRecent(Instant before, int limit, NotificationPriority priority,
                                           NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Soft delete: visible = false. Kayit tabloda kalir. */
    void hide(UUID id);

    /** Tum gorunur kayitlari gizler. */
    void hideAll();

    /** Verilen id'lere sahip kayitlari okundu olarak isaretler. Bos/null liste verilirse hicbir sey yapmaz. */
    void markAsRead(List<UUID> ids);

    /** Bir bildirimin "kaydedildi" durumunu ayarlar - read/hidden'in aksine geri alinabilir. */
    void setSaved(UUID id, boolean saved);

    /** Kaydedilmis (saved=true, visible=true) bildirimleri, zaman imlecli sayfalamayla doner. */
    List<Notification> findSaved(Instant before, int limit);

    /**
     * Baslik, icerik, tip, kaynak cihaz ve tarih alanlarinin HERHANGI
     * BIRINDE query'yi iceren (buyuk/kucuk harf duyarsiz) bildirimleri doner.
     */
    List<Notification> search(String query, Instant before, int limit);

    /** Toplam okunmamis bildirim sayisini doner (sayfalamadan bagimsiz). */
    default int countUnread() {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu okunmamis sayisini desteklemiyor");
    }

        /** Hedefli modda: tek bir bildirimi SADECE bu kullanici icin gizler. */
    default void hide(UUID id, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bu kullaniciya gorunen tum bildirimleri gizler. */
    default void hideAll(NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: verilen id'leri SADECE bu kullanici icin okundu isaretler. */
    default void markAsRead(List<UUID> ids, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bu kullaniciya gorunen okunmamis bildirim sayisini doner. */
    default int countUnreadForIdentity(NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu okunmamis sayisini desteklemiyor");
    }

    /** Hedefli modda: bir bildirimin "kaydedildi" durumunu SADECE bu kullanici icin ayarlar. */
    default void setSaved(UUID id, boolean saved, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bu kullanicinin kaydettigi bildirimleri doner. */
    default List<Notification> findSaved(Instant before, int limit, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bu kullaniciya gorunen bildirimler arasinda serbest metin arar. */
    default List<Notification> search(String query, Instant before, int limit, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationService implementasyonu hedefli bildirimi desteklemiyor");
    }
}