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

import io.github.bilalefeuysl.notification.core.config.NotificationSchemaInitializer;
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.repository.NotificationRepository;
import io.github.bilalefeuysl.notification.core.service.NotificationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kullanan uygulama kendi {@link NotificationRepository} bean'ini tanimlar ve
 * {@code notification.initialize-schema=false} yaparsa: kutuphane hicbir
 * DataSource'a dokunmaz, Flyway/schema-initializer bean'i kaydetmez, context
 * hic veritabani olmadan acilir ve servis kullanicinin repo'suna baglanir.
 *
 * Buradaki {@code InMemoryRepo}, {@code README.md} "Writing your own
 * NotificationRepository" bolumundeki ornekle birebir ayni - yani o ornegin
 * gercekten derlenip calistigi burada dogrulanmis oluyor.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = NotificationCustomRepositoryTest.TestApp.class,
        properties = {
                "notification.initialize-schema=false",
                "notification.websocket.enabled=false",
                "notification.rest.enabled=false"
        })
class NotificationCustomRepositoryTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    NotificationService notificationService;

    @Test
    void noDatabaseInfrastructureIsRegistered() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
        assertThat(context.getBeanNamesForType(NotificationSchemaInitializer.class)).isEmpty();
    }

    @Test
    void serviceUsesTheCustomRepository() {
        assertThat(context.getBean(NotificationRepository.class)).isInstanceOf(InMemoryRepo.class);

        Notification published = notificationService.publish("Test", "in-memory");

        assertThat(notificationService.findRecent(null, 10))
                .extracting(Notification::id)
                .contains(published.id());

        notificationService.hide(published.id());
        assertThat(notificationService.findRecent(null, 10))
                .extracting(Notification::id)
                .doesNotContain(published.id());
    }

    @SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
    static class TestApp {
        @Bean
        NotificationRepository notificationRepository() {
            return new InMemoryRepo();
        }
    }

    // --- README "Writing your own NotificationRepository" ornegiyle ayni ---

    /**
     * Bildirimleri yalnizca bellekte tutar - yeniden baslatinca kaybolur.
     * Testler, demolar ya da kaliciliga ihtiyaci olmayan tek dugum icin.
     * Hedefli bildirim (notification.targeting.enabled=true) DESTEKLENMEZ.
     */
    static class InMemoryRepo implements NotificationRepository {

        private final Map<UUID, Notification> store = new ConcurrentHashMap<>();

        @Override
        public Notification save(Notification notification) {
            store.put(notification.id(), notification);
            return notification;
        }

        @Override
        public Optional<Notification> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        private Stream<Notification> visibleNewestFirst(Instant before) {
            return store.values().stream()
                    .filter(Notification::visible)
                    .filter(n -> before == null || n.createdAt().isBefore(before))
                    .sorted(Comparator.comparing(Notification::createdAt).reversed());
        }

        @Override
        public List<Notification> findVisibleBefore(Instant before, int limit) {
            return visibleNewestFirst(before).limit(limit).toList();
        }

        @Override
        public List<Notification> findVisibleBefore(Instant before, int limit, NotificationPriority priority) {
            return visibleNewestFirst(before)
                    .filter(n -> priority == null || n.priority() == priority)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Notification> findSavedBefore(Instant before, int limit) {
            return visibleNewestFirst(before).filter(Notification::saved).limit(limit).toList();
        }

        @Override
        public List<Notification> searchVisibleBefore(String query, Instant before, int limit) {
            String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
            return visibleNewestFirst(before)
                    .filter(n -> contains(n.classification(), q) || contains(n.message(), q)
                            || contains(n.classificationEn(), q) || contains(n.messageEn(), q)
                            || contains(n.type(), q) || contains(n.sourceDeviceId(), q))
                    .limit(limit)
                    .toList();
        }

        private static boolean contains(String value, String lowerCaseQuery) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseQuery);
        }

        @Override
        public List<Notification> findVisibleSortedByPriority(NotificationPriority cursorPriority,
                Instant cursorCreatedAt, UUID cursorId, int limit) {
            Comparator<Notification> order = Comparator
                    .comparingInt((Notification n) -> n.priority().ordinal()).reversed()
                    .thenComparing(Notification::createdAt, Comparator.reverseOrder())
                    .thenComparing(Notification::id, Comparator.reverseOrder());

            return store.values().stream()
                    .filter(Notification::visible)
                    .sorted(order)
                    .filter(n -> cursorPriority == null
                            || afterCursor(n, cursorPriority, cursorCreatedAt, cursorId))
                    .limit(limit)
                    .toList();
        }

        private static boolean afterCursor(Notification n, NotificationPriority cp, Instant cc, UUID ci) {
            int byPriority = Integer.compare(cp.ordinal(), n.priority().ordinal());
            if (byPriority != 0) {
                return byPriority > 0;
            }
            int byTime = cc.compareTo(n.createdAt());
            if (byTime != 0) {
                return byTime > 0;
            }
            return ci.compareTo(n.id()) > 0;
        }

        @Override
        public boolean hide(UUID id) {
            Notification n = store.get(id);
            if (n == null || !n.visible()) {
                return false;
            }
            store.put(id, copy(n, false, n.read(), n.saved()));
            return true;
        }

        @Override
        public int hideAll() {
            int count = 0;
            for (Notification n : store.values()) {
                if (n.visible()) {
                    store.put(n.id(), copy(n, false, n.read(), n.saved()));
                    count++;
                }
            }
            return count;
        }

        @Override
        public int markAsRead(List<UUID> ids) {
            int count = 0;
            for (UUID id : ids) {
                Notification n = store.get(id);
                if (n != null && !n.read()) {
                    store.put(id, copy(n, n.visible(), true, n.saved()));
                    count++;
                }
            }
            return count;
        }

        @Override
        public boolean setSaved(UUID id, boolean saved) {
            Notification n = store.get(id);
            if (n == null) {
                return false;
            }
            store.put(id, copy(n, n.visible(), n.read(), saved));
            return true;
        }

        // countUnread() varsayilan implementasyonu is gorur; verim icin override edilebilir.

        private static Notification copy(Notification n, boolean visible, boolean read, boolean saved) {
            return new Notification(n.id(), n.classification(), n.message(),
                    n.classificationEn(), n.messageEn(), n.type(), n.priority(), n.sourceDeviceId(),
                    n.createdAt(), visible, read, saved, n.metadata(), n.audience());
        }
    }
}
