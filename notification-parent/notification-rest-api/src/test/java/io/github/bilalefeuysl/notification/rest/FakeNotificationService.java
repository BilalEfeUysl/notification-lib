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
package io.github.bilalefeuysl.notification.rest;

import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationCommand;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.service.NotificationService;
import io.github.bilalefeuysl.notification.core.model.NotificationAudience;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class FakeNotificationService implements NotificationService {

    private final Map<UUID, Notification> store = new LinkedHashMap<>();

    public FakeNotificationService() {
        seed("Basligi 1", "Icerik 1", Instant.parse("2026-08-17T10:00:00Z"));
        seed("Basligi 2", "Icerik 2", Instant.parse("2026-08-17T09:00:00Z"));
    }

    public UUID seed(String classification, String message, Instant createdAt) {
        return seed(classification, message, createdAt, NotificationPriority.NORMAL);
    }

    public UUID seed(String classification, String message, Instant createdAt, NotificationPriority priority) {
        UUID id = UUID.randomUUID();
        store.put(id, new Notification(id, classification, message, "INFO",
                priority, null, createdAt, true, false, false, Map.of(),
                new NotificationAudience.Everyone()));
        return id;
    }

    @Override
    public Notification publish(NotificationCommand command) {
        throw new UnsupportedOperationException("Bu testte kullanilmiyor");
    }

    @Override
    public List<Notification> findRecent(Instant before, int limit) {
        return findRecent(before, limit, (NotificationPriority) null);
    }

    @Override
    public List<Notification> findRecent(Instant before, int limit, NotificationIdentity identity) {
        return findRecent(before, limit);
    }

    @Override
    public List<Notification> findRecent(Instant before, int limit, NotificationPriority priority) {
        return store.values().stream()
                .filter(Notification::visible)
                .filter(n -> before == null || n.createdAt().isBefore(before))
                .filter(n -> priority == null || n.priority() == priority)
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void hide(UUID id) {
        Notification n = store.get(id);
        if (n != null) {
            store.put(id, new Notification(n.id(), n.classification(), n.message(), n.type(),
                    n.priority(), n.sourceDeviceId(), n.createdAt(), false, n.read(), n.saved(), n.metadata(),
                    n.audience()));
        }
    }

    @Override
    public void hideAll() {
        store.replaceAll((id, n) -> new Notification(n.id(), n.classification(), n.message(), n.type(),
                n.priority(), n.sourceDeviceId(), n.createdAt(), false, n.read(), n.saved(), n.metadata(),
                n.audience()));
    }
    @Override
    public void markAsRead(List<UUID> ids) {
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Notification n = store.get(id);
            if (n != null) {
                store.put(id, new Notification(n.id(), n.classification(), n.message(), n.type(),
                        n.priority(), n.sourceDeviceId(), n.createdAt(), n.visible(), true, n.saved(), n.metadata(),
                        n.audience()));
            }
        }
    }

    @Override
    public void setSaved(UUID id, boolean saved) {
        Notification n = store.get(id);
        if (n != null) {
            store.put(id, new Notification(n.id(), n.classification(), n.message(), n.type(),
                    n.priority(), n.sourceDeviceId(), n.createdAt(), n.visible(), n.read(), saved, n.metadata(),
                    n.audience()));
        }
    }

    @Override
    public List<Notification> findSaved(Instant before, int limit) {
        return store.values().stream()
                .filter(Notification::visible)
                .filter(Notification::saved)
                .filter(n -> before == null || n.createdAt().isBefore(before))
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<Notification> findRecentSortedByPriority(NotificationPriority cursorPriority, Instant cursorCreatedAt,
                                                           UUID cursorId, int limit) {
        Comparator<Notification> byPriorityThenDate = Comparator
                .comparingInt((Notification n) -> priorityRank(n.priority())).reversed()
                .thenComparing(Comparator.comparing(Notification::createdAt).reversed());

        List<Notification> sorted = store.values().stream()
                .filter(Notification::visible)
                .sorted(byPriorityThenDate)
                .collect(Collectors.toList());

        if (cursorCreatedAt == null) {
            return sorted.stream().limit(limit).collect(Collectors.toList());
        }
        int cursorRank = priorityRank(cursorPriority);
        return sorted.stream()
                .dropWhile(n -> !(priorityRank(n.priority()) < cursorRank
                        || (priorityRank(n.priority()) == cursorRank && n.createdAt().isBefore(cursorCreatedAt))
                        || (priorityRank(n.priority()) == cursorRank && n.createdAt().equals(cursorCreatedAt) && n.id().compareTo(cursorId) < 0)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static int priorityRank(NotificationPriority priority) {
        return switch (priority) {
            case HIGH -> 2;
            case NORMAL -> 1;
            case LOW -> 0;
        };
    }

    @Override
    public List<Notification> search(String query, Instant before, int limit) {
        String needle = query.toLowerCase();
        return store.values().stream()
                .filter(Notification::visible)
                .filter(n -> before == null || n.createdAt().isBefore(before))
                .filter(n -> n.classification().toLowerCase().contains(needle)
                        || n.message().toLowerCase().contains(needle)
                        || n.type().toLowerCase().contains(needle))
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}