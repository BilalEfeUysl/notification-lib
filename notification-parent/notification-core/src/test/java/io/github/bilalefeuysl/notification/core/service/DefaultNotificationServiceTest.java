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
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationAudience;
import io.github.bilalefeuysl.notification.core.model.NotificationCommand;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.model.NotificationType;
import io.github.bilalefeuysl.notification.core.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultNotificationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-19T08:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    void publish_kaydi_olusturur_ve_repository_uzerinden_kaydeder() {
        RecordingRepository repository = new RecordingRepository();
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        DefaultNotificationService service = new DefaultNotificationService(repository, broadcaster, FIXED_CLOCK);

        Notification result = service.publish(NotificationCommand.builder()
                .classification("Test Basligi")
                .message("Test mesaji")
                .type(NotificationType.WARNING)
                .priority(NotificationPriority.HIGH)
                .build());

        assertThat(result.classification()).isEqualTo("Test Basligi");
        assertThat(result.type()).isEqualTo("WARNING");
        assertThat(result.priority()).isEqualTo(NotificationPriority.HIGH);
        assertThat(result.visible()).isTrue();
        assertThat(result.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(repository.saved).containsExactly(result);
    }

    @Test
    void publish_ingilizce_metni_command_uzerinden_tasir() {
        RecordingRepository repository = new RecordingRepository();
        DefaultNotificationService service =
                new DefaultNotificationService(repository, new RecordingBroadcaster(), FIXED_CLOCK);

        Notification result = service.publish(NotificationCommand.builder()
                .classification("Bakim")
                .message("Sistem kapanacak")
                .classificationEn("Maintenance")
                .messageEn("System goes down")
                .build());

        assertThat(result.classificationEn()).isEqualTo("Maintenance");
        assertThat(result.messageEn()).isEqualTo("System goes down");
        assertThat(result.resolvedClassification("en")).isEqualTo("Maintenance");
        assertThat(result.resolvedClassification("tr")).isEqualTo("Bakim");
        assertThat(repository.saved).containsExactly(result);
    }

    @Test
    void command_ingilizce_alanlardan_yalnizca_biri_verilirse_hata_firlatir() {
        assertThatThrownBy(() -> NotificationCommand.builder()
                .classification("Bakim").message("Sistem kapanacak")
                .classificationEn("Maintenance")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publish_kisayol_varsayilan_tip_ve_oncelik_ile_calisir() {
        DefaultNotificationService service =
                new DefaultNotificationService(new RecordingRepository(), new RecordingBroadcaster(), FIXED_CLOCK);

        Notification result = service.publish("Basit Baslik", "Basit mesaj");

        assertThat(result.type()).isEqualTo("INFO");
        assertThat(result.priority()).isEqualTo(NotificationPriority.NORMAL);
    }

    @Test
    void publish_kaydedilen_bildirimi_yayinlar() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        DefaultNotificationService service =
                new DefaultNotificationService(new RecordingRepository(), broadcaster, FIXED_CLOCK);

        Notification result = service.publish("Baslik", "Mesaj");

        assertThat(broadcaster.broadcasted).containsExactly(result);
    }

    @Test
    void publish_yayin_hatasinda_bile_kaydi_dondurur() {
        RecordingRepository repository = new RecordingRepository();
        NotificationBroadcaster failingBroadcaster = new NotificationBroadcaster() {
            @Override
            public void broadcast(Notification notification) {
                throw new IllegalStateException("yayin basarisiz");
            }

            @Override
            public void broadcastHidden(List<UUID> ids) {
                // bu testte kullanilmiyor
            }

            @Override
            public void broadcastAllHidden() {
                // bu testte kullanilmiyor
            }

            @Override
            public void broadcastRead(List<UUID> ids) {
                // bu testte kullanilmiyor
            }
        };
        DefaultNotificationService service =
                new DefaultNotificationService(repository, failingBroadcaster, FIXED_CLOCK);

        Notification result = service.publish("Baslik", "Mesaj");

        assertThat(result).isNotNull();
        assertThat(repository.saved).containsExactly(result);
    }

    @Test
    void findRecent_limit_sifir_veya_negatifse_hata_firlatir() {
        DefaultNotificationService service =
                new DefaultNotificationService(new RecordingRepository(), new RecordingBroadcaster());

        assertThatThrownBy(() -> service.findRecent(null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findRecentSortedByPriority_limit_sifir_veya_negatifse_hata_firlatir() {
        DefaultNotificationService service =
                new DefaultNotificationService(new RecordingRepository(), new RecordingBroadcaster());

        assertThatThrownBy(() -> service.findRecentSortedByPriority(null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findRecentSortedByPriority_imlec_parcalarindan_bazisi_null_bazisi_doluysa_hata_firlatir() {
        DefaultNotificationService service =
                new DefaultNotificationService(new RecordingRepository(), new RecordingBroadcaster());

        assertThatThrownBy(() -> service.findRecentSortedByPriority(NotificationPriority.HIGH, null, null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findRecentSortedByPriority(null, FIXED_NOW, UUID.randomUUID(), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findRecentSortedByPriority_repository_metodunu_dogru_parametrelerle_cagirir() {
        UUID cursorId = UUID.randomUUID();
        List<Object> capturedArgs = new ArrayList<>();
        RecordingRepository repository = new RecordingRepository() {
            @Override
            public List<Notification> findVisibleSortedByPriority(NotificationPriority cursorPriority,
                    Instant cursorCreatedAt, UUID cursorIdArg, int limit) {
                capturedArgs.add(cursorPriority);
                capturedArgs.add(cursorCreatedAt);
                capturedArgs.add(cursorIdArg);
                capturedArgs.add(limit);
                return List.of();
            }
        };
        DefaultNotificationService service = new DefaultNotificationService(repository, new RecordingBroadcaster());

        service.findRecentSortedByPriority(NotificationPriority.HIGH, FIXED_NOW, cursorId, 5);

        assertThat(capturedArgs).containsExactly(NotificationPriority.HIGH, FIXED_NOW, cursorId, 5);
    }

    @Test
    void hide_basariliysa_gizleme_olayini_yayinlar() {
        RecordingRepository repository = new RecordingRepository();
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        DefaultNotificationService service = new DefaultNotificationService(repository, broadcaster, FIXED_CLOCK);
        UUID id = UUID.randomUUID();

        service.hide(id);

        assertThat(broadcaster.hiddenCalls).containsExactly(List.of(id));
    }

    @Test
    void hide_kayit_bulunamadiysa_yayin_yapmaz() {
        RecordingRepository repository = new RecordingRepository() {
            @Override
            public boolean hide(UUID id) {
                return false;
            }
        };
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        DefaultNotificationService service = new DefaultNotificationService(repository, broadcaster, FIXED_CLOCK);

        service.hide(UUID.randomUUID());

        assertThat(broadcaster.hiddenCalls).isEmpty();
    }

    @Test
    void hideAll_kayit_gizlendiyse_tumunu_gizleme_olayini_yayinlar() {
        RecordingRepository repository = new RecordingRepository();
        repository.save(new Notification(UUID.randomUUID(), "Baslik", "Mesaj", "INFO",
                NotificationPriority.NORMAL, null, FIXED_NOW, true, false, false, Map.of(),
                new NotificationAudience.Everyone()));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        DefaultNotificationService service = new DefaultNotificationService(repository, broadcaster, FIXED_CLOCK);

        service.hideAll();

        assertThat(broadcaster.allHiddenCallCount).isEqualTo(1);
    }

    @Test
    void hideAll_gizlenecek_kayit_yoksa_yayin_yapmaz() {
        RecordingRepository repository = new RecordingRepository();
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        DefaultNotificationService service = new DefaultNotificationService(repository, broadcaster, FIXED_CLOCK);

        service.hideAll();

        assertThat(broadcaster.allHiddenCallCount).isEqualTo(0);
    }

    @Test
    void markAsRead_basariliysa_okundu_olayini_yayinlar() {
        RecordingRepository repository = new RecordingRepository();
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        DefaultNotificationService service = new DefaultNotificationService(repository, broadcaster, FIXED_CLOCK);
        UUID id = UUID.randomUUID();

        service.markAsRead(List.of(id));

        assertThat(broadcaster.readCalls).containsExactly(List.of(id));
    }

    @Test
    void markAsRead_bos_liste_ise_yayin_yapmaz() {
        RecordingRepository repository = new RecordingRepository();
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        DefaultNotificationService service = new DefaultNotificationService(repository, broadcaster, FIXED_CLOCK);

        service.markAsRead(List.of());

        assertThat(broadcaster.readCalls).isEmpty();
    }

    private static class RecordingRepository implements NotificationRepository {
        private final List<Notification> saved = new ArrayList<>();
        private final List<UUID> savedIds = new ArrayList<>();

        @Override
        public Notification save(Notification notification) {
            saved.add(notification);
            return notification;
        }

        @Override
        public Optional<Notification> findById(UUID id) {
            return saved.stream().filter(n -> n.id().equals(id)).findFirst();
        }

        @Override
        public List<Notification> findVisibleBefore(Instant before, int limit) {
            return List.copyOf(saved);
        }

        @Override
        public List<Notification> findVisibleBefore(Instant before, int limit, NotificationPriority priority) {
            return List.copyOf(saved);
        }

        @Override
        public boolean hide(UUID id) {
            return true;
        }

        @Override
        public int hideAll() {
            return saved.size();
        }

        @Override
        public int markAsRead(List<UUID> ids) {
            return ids == null ? 0 : ids.size();
        }

        @Override
        public boolean setSaved(UUID id, boolean value) {
            if (value) {
                if (!savedIds.contains(id)) {
                    savedIds.add(id);
                }
            } else {
                savedIds.remove(id);
            }
            return true;
        }

        @Override
        public List<Notification> findSavedBefore(Instant before, int limit) {
            return saved.stream().filter(n -> savedIds.contains(n.id())).toList();
        }

        @Override
        public List<Notification> searchVisibleBefore(String query, Instant before, int limit) {
            String needle = query.toLowerCase();
            return saved.stream()
                    .filter(n -> n.classification().toLowerCase().contains(needle)
                            || n.message().toLowerCase().contains(needle))
                    .toList();
        }

        @Override
        public List<Notification> findVisibleSortedByPriority(NotificationPriority cursorPriority,
                Instant cursorCreatedAt, UUID cursorId, int limit) {
            return List.copyOf(saved);
        }
    }

    private static class RecordingBroadcaster implements NotificationBroadcaster {
        private final List<Notification> broadcasted = new ArrayList<>();
        private final List<List<UUID>> hiddenCalls = new ArrayList<>();
        private int allHiddenCallCount = 0;
        private final List<List<UUID>> readCalls = new ArrayList<>();

        @Override
        public void broadcast(Notification notification) {
            broadcasted.add(notification);
        }

        @Override
        public void broadcastHidden(List<UUID> ids) {
            hiddenCalls.add(ids);
        }

        @Override
        public void broadcastAllHidden() {
            allHiddenCallCount++;
        }

        @Override
        public void broadcastRead(List<UUID> ids) {
            readCalls.add(ids);
        }
    }
}