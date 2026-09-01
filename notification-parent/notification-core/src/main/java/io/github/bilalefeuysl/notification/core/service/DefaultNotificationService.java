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

import io.github.bilalefeuysl.notification.core.broadcast.NotificationBroadcaster;
import io.github.bilalefeuysl.notification.core.event.NotificationPublishedEvent;
import org.springframework.context.ApplicationEventPublisher;
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationCommand;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.repository.NotificationRepository;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DefaultNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotificationService.class);

    private final NotificationRepository repository;
    private final NotificationBroadcaster broadcaster;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public DefaultNotificationService(NotificationRepository repository,
                                      NotificationBroadcaster broadcaster) {
        this(repository, broadcaster, Clock.systemUTC(), null);
    }

    /** Clock disaridan verilebilir; testlerde sabit zaman kullanmayi saglar. */
    public DefaultNotificationService(NotificationRepository repository,
                                      NotificationBroadcaster broadcaster,
                                      Clock clock) {
        this(repository, broadcaster, clock, null);
    }

    /**
     * eventPublisher verilirse, her basarili publish() sonrasi NotificationPublishedEvent
     * yayinlanir. Spring context'i disinda (orn. testlerde) null verilebilir, o zaman
     * olay yayinlama basitce atlanir.
     */
    public DefaultNotificationService(NotificationRepository repository,
                                      NotificationBroadcaster broadcaster,
                                      ApplicationEventPublisher eventPublisher) {
        this(repository, broadcaster, Clock.systemUTC(), eventPublisher);
    }

    public DefaultNotificationService(NotificationRepository repository,
                                      NotificationBroadcaster broadcaster,
                                      Clock clock,
                                      ApplicationEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository null olamaz");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster null olamaz");
        this.clock = Objects.requireNonNull(clock, "clock null olamaz");
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Notification publish(NotificationCommand command) {
        Objects.requireNonNull(command, "command null olamaz");

        Notification notification = new Notification(
            UUID.randomUUID(),
            command.classification(),
            command.message(),
            command.classificationEn(),
            command.messageEn(),
            command.type(),
            command.priority(),
            command.sourceDeviceId(),
            clock.instant().truncatedTo(ChronoUnit.MICROS),
            true,
            false, // yeni yayinlanan bildirim her zaman okunmamis baslar
            false, // ve hic kaydedilmemis baslar
            command.metadata(),
            command.audience());

        Notification saved = repository.save(notification);

        // Yayin hatasi kaydi gecersiz kilmaz: kayit DB'de duruyor,
        // istemci sayfayi yenileyince REST ile gorur.
        try {
            broadcaster.broadcast(saved);
        } catch (RuntimeException ex) {
            log.warn("Bildirim yayinlanamadi, kayit yine de saklandi: id={}", saved.id(), ex);
        }

        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(new NotificationPublishedEvent(saved));
            } catch (RuntimeException ex) {
                log.warn("NotificationPublishedEvent yayinlanamadi: id={}", saved.id(), ex);
            }
        }

        return saved;
    }

        @Override
    public List<Notification> findRecent(Instant before, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        return repository.findVisibleBefore(before, limit);
    }

    @Override
    public List<Notification> findRecent(Instant before, int limit, NotificationPriority priority) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        return repository.findVisibleBefore(before, limit, priority);
    }

        @Override
    public List<Notification> findRecent(Instant before, int limit, NotificationIdentity identity) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        return repository.findVisibleForIdentity(before, limit, identity);
    }

    @Override
    public List<Notification> findRecent(Instant before, int limit, NotificationPriority priority,
                                          NotificationIdentity identity) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        return repository.findVisibleForIdentity(before, limit, priority, identity);
    }

    @Override
    public List<Notification> findRecentSortedByPriority(NotificationPriority cursorPriority, Instant cursorCreatedAt,
                                                           UUID cursorId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        if ((cursorPriority == null) != (cursorCreatedAt == null) || (cursorCreatedAt == null) != (cursorId == null)) {
            throw new IllegalArgumentException("cursorPriority/cursorCreatedAt/cursorId ya HEPSI null ya da HICBIRI null olmali");
        }
        return repository.findVisibleSortedByPriority(cursorPriority, cursorCreatedAt, cursorId, limit);
    }

    @Override
    public List<Notification> findRecentSortedByPriority(NotificationPriority cursorPriority, Instant cursorCreatedAt,
            UUID cursorId, int limit, NotificationIdentity identity) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        Objects.requireNonNull(identity, "identity null olamaz");
        if ((cursorPriority == null) != (cursorCreatedAt == null) || (cursorCreatedAt == null) != (cursorId == null)) {
            throw new IllegalArgumentException("cursorPriority/cursorCreatedAt/cursorId ya HEPSI null ya da HICBIRI null olmali");
        }
        return repository.findVisibleSortedByPriorityForIdentity(cursorPriority, cursorCreatedAt, cursorId, limit, identity);
    }

    @Override
    public void hide(UUID id) {
        Objects.requireNonNull(id, "id null olamaz");
        boolean changed = repository.hide(id);
        if (!changed) {
            log.debug("Gizlenecek gorunur kayit bulunamadi: id={}", id);
            return;
        }
        try {
            broadcaster.broadcastHidden(List.of(id));
        } catch (RuntimeException ex) {
            log.warn("Gizleme olayi yayinlanamadi: id={}", id, ex);
        }
    }

    @Override
    public void hideAll() {
        int count = repository.hideAll();
        log.debug("{} kayit gizlendi", count);
        if (count > 0) {
            try {
                broadcaster.broadcastAllHidden();
            } catch (RuntimeException ex) {
                log.warn("Tumunu gizleme olayi yayinlanamadi", ex);
            }
        }
    }

    @Override
    public void markAsRead(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int count = repository.markAsRead(ids);
        log.debug("{} kayit okundu olarak isaretlendi", count);
        if (count > 0) {
            try {
                broadcaster.broadcastRead(ids);
            } catch (RuntimeException ex) {
                log.warn("Okundu olayi yayinlanamadi", ex);
            }
        }
    }

    @Override
    public int countUnread() {
        return repository.countUnread();
    }

    @Override
    public void setSaved(UUID id, boolean saved) {
        Objects.requireNonNull(id, "id null olamaz");
        repository.setSaved(id, saved);
    }

    @Override
    public List<Notification> findSaved(Instant before, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        return repository.findSavedBefore(before, limit);
    }

    @Override
    public List<Notification> search(String query, Instant before, int limit) {
        Objects.requireNonNull(query, "query null olamaz");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        return repository.searchVisibleBefore(query, before, limit);
    }

        @Override
    public void hide(UUID id, NotificationIdentity identity) {
        Objects.requireNonNull(id, "id null olamaz");
        Objects.requireNonNull(identity, "identity null olamaz");
        boolean changed = repository.hideForIdentity(id, identity);
        if (!changed) {
            log.debug("Gizlenecek gorunur kayit bulunamadi: id={}, userId={}", id, identity.userId());
            return;
        }
        try {
            broadcaster.broadcastHiddenForUser(List.of(id), identity);
        } catch (RuntimeException ex) {
            log.warn("Gizleme olayi yayinlanamadi: id={}", id, ex);
        }
    }

    @Override
    public void hideAll(NotificationIdentity identity) {
        Objects.requireNonNull(identity, "identity null olamaz");
        int count = repository.hideAllForIdentity(identity);
        log.debug("{} kayit gizlendi (userId={})", count, identity.userId());
        if (count > 0) {
            try {
                broadcaster.broadcastAllHiddenForUser(identity);
            } catch (RuntimeException ex) {
                log.warn("Tumunu gizleme olayi yayinlanamadi", ex);
            }
        }
    }

    @Override
    public void markAsRead(List<UUID> ids, NotificationIdentity identity) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Objects.requireNonNull(identity, "identity null olamaz");
        int count = repository.markAsReadForIdentity(ids, identity);
        log.debug("{} kayit okundu olarak isaretlendi (userId={})", count, identity.userId());
        if (count > 0) {
            try {
                broadcaster.broadcastReadForUser(ids, identity);
            } catch (RuntimeException ex) {
                log.warn("Okundu olayi yayinlanamadi", ex);
            }
        }
    }

    @Override
    public int countUnreadForIdentity(NotificationIdentity identity) {
        Objects.requireNonNull(identity, "identity null olamaz");
        return repository.countUnreadForIdentity(identity);
    }

    @Override
    public void setSaved(UUID id, boolean saved, NotificationIdentity identity) {
        Objects.requireNonNull(id, "id null olamaz");
        Objects.requireNonNull(identity, "identity null olamaz");
        repository.setSavedForIdentity(id, saved, identity);
    }

    @Override
    public List<Notification> findSaved(Instant before, int limit, NotificationIdentity identity) {
        Objects.requireNonNull(identity, "identity null olamaz");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        return repository.findSavedForIdentity(before, limit, identity);
    }

    @Override
    public List<Notification> search(String query, Instant before, int limit, NotificationIdentity identity) {
        Objects.requireNonNull(query, "query null olamaz");
        Objects.requireNonNull(identity, "identity null olamaz");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 0'dan buyuk olmalidir");
        }
        return repository.searchVisibleForIdentity(query, before, limit, identity);
    }
}
