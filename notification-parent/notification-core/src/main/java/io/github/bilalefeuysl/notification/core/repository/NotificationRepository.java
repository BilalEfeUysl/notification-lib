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
package io.github.bilalefeuysl.notification.core.repository;

import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;


import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bildirimlerin kalici depolanmasindan sorumlu katman. Varsayilan implementasyon
 * (JdbcNotificationRepository) PostgreSQL kullanir. Kendi implementasyonunuzu
 * yazip Spring context'e NotificationRepository turunde bir bean eklerseniz,
 * kutuphanenin varsayilani otomatik olarak devre disi kalir.
 */
public interface NotificationRepository {

    /** Kaydi ekler ve eklenen halini doner. */
    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    /**
     * Sadece visible = true kayitlari, created_at DESC sirasiyla doner.
     *
     * @param before null ise en yeniden baslar; doluysa bu andan eski kayitlar doner
     */
    List<Notification> findVisibleBefore(Instant before, int limit);

    /** findVisibleBefore(before, limit) ile ayni, ek olarak priority'ye gore suzer. priority null ise suzme yapilmaz. */
    List<Notification> findVisibleBefore(Instant before, int limit, NotificationPriority priority);

    /**
     * findVisibleBefore ile AYNI kayitlari, ama farkli sirada doner: once
     * onceliğe gore (HIGH -> NORMAL -> LOW), sonra created_at DESC'e gore.
     * Bu TAMAMEN ayri, opt-in bir sorgu yolu - varsayilan findVisibleBefore()
     * cagrilarini hicbir sekilde etkilemez, priority sıralamasi istemeyen
     * tuketiciler icin fazladan hesaplama YAPILMAZ.
     *
     * Tek bir Instant imleç yetmez (ayni created_at farkli oncelikte
     * olabilir) - imlec uc parcali: bir onceki sayfanin SON kaydinin
     * (priority, createdAt, id) uclusu. Ilk sayfa icin ucu de null verilir.
     *
     * @return kayitlar; limit+1 istenirse hasMore hesaplamak cagiran tarafin sorumlulugundadir
     */
    List<Notification> findVisibleSortedByPriority(NotificationPriority cursorPriority, Instant cursorCreatedAt,
                                                     UUID cursorId, int limit);

    /** @return kayit gercekten gizlendiyse true */
    boolean hide(UUID id);

    /** @return gizlenen kayit sayisi */
    int hideAll();

    /** Verilen id'lere sahip kayitlari okundu (read = true) olarak isaretler. @return guncellenen kayit sayisi */
    int markAsRead(List<UUID> ids);

    /**
     * Bir kaydin "kaydedildi" durumunu ayarlar - read/hidden'in aksine GERI
     * ALINABILIR (saved=false ile kaydi kaldirilabilir).
     * @return kayit gercekten bulunup guncellendiyse true
     */
    boolean setSaved(UUID id, boolean saved);

    /** Sadece visible=true VE saved=true kayitlari, created_at DESC sirasiyla doner. */
    List<Notification> findSavedBefore(Instant before, int limit);

    /**
     * Serbest metin arama - classification, message, type, sourceDeviceId ve
     * bicimlendirilmis tarih ("gg.aa.yyyy ss:dd") alanlarinin HERHANGI
     * BIRINDE (buyuk/kucuk harf duyarsiz) query'yi icerenleri doner.
     */
    List<Notification> searchVisibleBefore(String query, Instant before, int limit);

    /**
     * Toplam okunmamis (visible=true, read=false) kayit sayisini doner -
     * sayfalamadan tamamen bagimsiz, rozette "kac tane var" gostermek icin.
     * Varsayilan implementasyon TUM kayitlari cekip sayar (calisir ama
     * verimsiz) - JdbcNotificationRepository kendi SQL COUNT sorgusuyla
     * bunu override ediyor, gercekte hep o kullanilacak.
     */
    default int countUnread() {
        return (int) findVisibleBefore(null, Integer.MAX_VALUE).stream().filter(n -> !n.read()).count();
    }

        /**
     * Hedefli bildirim modu icin: sadece verilen kimlige uygun (Everyone,
     * kendi userId'si, ya da rollerinden biri) kayitlari doner. Varsayilan
     * implementasyon desteklemiyor - kendi repository'nizi yazip hedeflemeyi
     * desteklemek isterseniz bu iki metodu override edin.
     */
    default List<Notification> findVisibleForIdentity(Instant before, int limit, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }

    default List<Notification> findVisibleForIdentity(Instant before, int limit, NotificationPriority priority,
                                                        NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }
        /** Hedefli modda: tek bir bildirimi SADECE bu kullanici icin gizler. @return gercekten degisti mi */
    default boolean hideForIdentity(UUID id, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bu kullaniciya gorunen tum bildirimleri gizler. @return gizlenen kayit sayisi */
    default int hideAllForIdentity(NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: verilen id'leri SADECE bu kullanici icin okundu isaretler. @return guncellenen kayit sayisi */
    default int markAsReadForIdentity(List<UUID> ids, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bu kullaniciya gorunen okunmamis kayit sayisini doner. */
    default int countUnreadForIdentity(NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bir kaydin "kaydedildi" durumunu SADECE bu kullanici icin ayarlar. @return gercekten degisti mi */
    default boolean setSavedForIdentity(UUID id, boolean saved, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bu kullanicinin kaydettigi bildirimleri doner. */
    default List<Notification> findSavedForIdentity(Instant before, int limit, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: bu kullaniciya gorunen bildirimler arasinda serbest metin arar. */
    default List<Notification> searchVisibleForIdentity(String query, Instant before, int limit,
                                                          NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }

    /** Hedefli modda: findVisibleSortedByPriority ile ayni, sadece bu kullaniciya gorunen kayitlarla. */
    default List<Notification> findVisibleSortedByPriorityForIdentity(NotificationPriority cursorPriority,
            Instant cursorCreatedAt, UUID cursorId, int limit, NotificationIdentity identity) {
        throw new UnsupportedOperationException(
                "Bu NotificationRepository implementasyonu hedefli bildirimi desteklemiyor");
    }
}
