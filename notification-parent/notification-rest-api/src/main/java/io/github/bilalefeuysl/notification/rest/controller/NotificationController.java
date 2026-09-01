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
package io.github.bilalefeuysl.notification.rest.controller;

import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.service.NotificationService;
import io.github.bilalefeuysl.notification.rest.dto.NotificationDto;
import io.github.bilalefeuysl.notification.rest.dto.NotificationPageResponse;
import io.github.bilalefeuysl.notification.rest.dto.PriorityCursor;
import io.github.bilalefeuysl.notification.rest.dto.SetSavedRequest;
import io.github.bilalefeuysl.notification.rest.dto.UnreadCountResponse;
import io.github.bilalefeuysl.notification.rest.error.InvalidNotificationRequestException;
import io.github.bilalefeuysl.notification.rest.identity.NotificationIdentityResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// CORS burada @CrossOrigin ile SABIT olarak acilmaz; capraz-origin izni
// notification.cors.allowed-origins ayarindan gelir ve
// NotificationRestAutoConfiguration icinde uygulanir.
@RestController
public class NotificationController {

    private final NotificationService notificationService;
    private final int defaultLimit;
    private final int maxLimit;
    private final boolean targetingEnabled;
    private final NotificationIdentityResolver identityResolver;


    public NotificationController(NotificationService notificationService, int defaultLimit, int maxLimit,
                                   boolean targetingEnabled, NotificationIdentityResolver identityResolver) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService null olamaz");
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
        this.targetingEnabled = targetingEnabled;
        this.identityResolver = identityResolver;

        // Yanlis yapilandirmayi CALISMA ANINDA degil, nesne olusturulurken
        // yakala. targeting acikken resolver yoksa her istek NullPointerException
        // ile patlardi; boyle bir durumda uygulamanin hic acilmamasi daha iyidir.
        if (targetingEnabled && identityResolver == null) {
            throw new IllegalStateException(
                    "notification.targeting.enabled=true ama NotificationIdentityResolver verilmedi. "
                            + "Kutuphanenin varsayilani (HeaderNotificationIdentityResolver) otomatik "
                            + "devreye girer; kendi bean'inizi tanimladiysaniz tipinin "
                            + "io.github.bilalefeuysl.notification.rest.identity.NotificationIdentityResolver "
                            + "oldugundan emin olun.");
        }
    }

        @GetMapping
    public NotificationPageResponse list(
            HttpServletRequest request,
            @RequestParam(required = false) Instant before,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) NotificationPriority priority,
            @RequestParam(required = false) Boolean saved,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String priorityCursor) {

        if (limit != null && limit <= 0) {
            throw new InvalidNotificationRequestException("limit 0'dan buyuk olmalidir");
        }

        int effectiveLimit = (limit == null) ? defaultLimit : Math.min(limit, maxLimit);

        // sort=priority TAMAMEN ayri, opt-in bir yol - q/saved/priority ile
        // BIRLIKTE kullanilmiyor (digerleri gibi, basit tutmak icin). Bu dal
        // girilmedigi surece (sort parametresi verilmediginde) asagidaki
        // eski kod yolu HICBIR SEKILDE etkilenmez - priority sıralamasi
        // istemeyen tuketiciler icin fazladan hesaplama yapilmaz.
        if ("priority".equals(sort)) {
            if ((q != null && !q.isBlank()) || Boolean.TRUE.equals(saved) || priority != null) {
                throw new InvalidNotificationRequestException(
                        "sort=priority; q/saved/priority parametreleriyle birlikte kullanilamaz");
            }
            PriorityCursor cursor = PriorityCursor.decode(priorityCursor);
            List<Notification> sortedFetched = targetingEnabled
                    ? notificationService.findRecentSortedByPriority(cursor.priority(), cursor.createdAt(),
                            cursor.id(), effectiveLimit + 1, identityResolver.resolve(request))
                    : notificationService.findRecentSortedByPriority(cursor.priority(), cursor.createdAt(),
                            cursor.id(), effectiveLimit + 1);

            boolean sortedHasMore = sortedFetched.size() > effectiveLimit;
            List<Notification> sortedPage = sortedHasMore ? sortedFetched.subList(0, effectiveLimit) : sortedFetched;
            String nextCursor = sortedHasMore ? PriorityCursor.encode(sortedPage.get(sortedPage.size() - 1)) : null;
            List<NotificationDto> sortedItems = sortedPage.stream().map(NotificationDto::from).toList();

            return new NotificationPageResponse(sortedItems, sortedHasMore, null, nextCursor);
        }

        // q verilirse serbest metin arama yapilir, saved=true SADECE
        // kaydedilmis bildirimleri doner - ucu de priority suzgeciyle
        // BIRLIKTE kullanilmiyor (basit tutmak icin, pratikte ihtiyac olmadi).
        List<Notification> fetched;
        if (q != null && !q.isBlank()) {
            fetched = targetingEnabled
                    ? notificationService.search(q, before, effectiveLimit + 1, identityResolver.resolve(request))
                    : notificationService.search(q, before, effectiveLimit + 1);
        } else if (Boolean.TRUE.equals(saved)) {
            fetched = targetingEnabled
                    ? notificationService.findSaved(before, effectiveLimit + 1, identityResolver.resolve(request))
                    : notificationService.findSaved(before, effectiveLimit + 1);
        } else if (targetingEnabled) {
            NotificationIdentity identity = identityResolver.resolve(request);
            fetched = (priority == null)
                    ? notificationService.findRecent(before, effectiveLimit + 1, identity)
                    : notificationService.findRecent(before, effectiveLimit + 1, priority, identity);
        } else {
            fetched = (priority == null)
                    ? notificationService.findRecent(before, effectiveLimit + 1)
                    : notificationService.findRecent(before, effectiveLimit + 1, priority);
        }

        boolean hasMore = fetched.size() > effectiveLimit;
        List<Notification> page = hasMore ? fetched.subList(0, effectiveLimit) : fetched;

        Instant nextBefore = hasMore ? page.get(page.size() - 1).createdAt() : null;

        List<NotificationDto> items = page.stream().map(NotificationDto::from).toList();

        return new NotificationPageResponse(items, hasMore, nextBefore);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(HttpServletRequest request) {
        int count = targetingEnabled
                ? notificationService.countUnreadForIdentity(identityResolver.resolve(request))
                : notificationService.countUnread();
        return new UnreadCountResponse(count);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hide(HttpServletRequest request, @PathVariable UUID id) {
        if (targetingEnabled) {
            notificationService.hide(id, identityResolver.resolve(request));
        } else {
            notificationService.hide(id);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> hideAll(HttpServletRequest request) {
        if (targetingEnabled) {
            notificationService.hideAll(identityResolver.resolve(request));
        } else {
            notificationService.hideAll();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * ids listesi SINIRLI: bir istekle sinirsiz sayida id gonderilebilseydi,
     * tek bir cagri devasa bir {@code IN (...)} sorgusu uretip veritabanini
     * kilitleyebilirdi. Ust sinir, sayfa basina donen en fazla kayit sayisiyla
     * (maxLimit) ayni tutuluyor - istemci zaten tek seferde bundan fazlasini
     * goremedigi icin bundan fazlasini okundu isaretlemesi de gerekmez.
     */
    @PatchMapping("/read")
    public ResponseEntity<Void> markAsRead(HttpServletRequest request, @RequestBody List<UUID> ids) {
        if (ids != null && ids.size() > maxLimit) {
            throw new InvalidNotificationRequestException(
                    "tek istekte en fazla " + maxLimit + " id okundu isaretlenebilir (gonderilen: " + ids.size() + ")");
        }
        if (targetingEnabled) {
            notificationService.markAsRead(ids, identityResolver.resolve(request));
        } else {
            notificationService.markAsRead(ids);
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/saved")
    public ResponseEntity<Void> setSaved(HttpServletRequest request, @PathVariable UUID id,
                                          @RequestBody SetSavedRequest body) {
        if (targetingEnabled) {
            notificationService.setSaved(id, body.saved(), identityResolver.resolve(request));
        } else {
            notificationService.setSaved(id, body.saved());
        }
        return ResponseEntity.noContent().build();
    }
}