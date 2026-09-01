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

import com.zaxxer.hikari.HikariDataSource;
import io.github.bilalefeuysl.notification.core.model.NotificationAudience;
import io.github.bilalefeuysl.notification.core.config.NotificationSchemaInitializer;
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcNotificationRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static HikariDataSource dataSource;
    JdbcNotificationRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        new NotificationSchemaInitializer(dataSource, "public", "notifications").migrate();
    }

    @AfterAll
    static void tearDownDatabase() {
        dataSource.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        repository = new JdbcNotificationRepository(dataSource, "public", "notifications");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.notifications");
        }
    }

    private Notification sample(String classification, Instant createdAt) {
        return new Notification(UUID.randomUUID(), classification, "mesaj", "INFO",
                NotificationPriority.NORMAL, null, createdAt, true, false, false, Map.of(),
                new NotificationAudience.Everyone());
    }

    private Notification sample(String classification, Instant createdAt, NotificationPriority priority) {
        return new Notification(UUID.randomUUID(), classification, "mesaj", "INFO",
                priority, null, createdAt, true, false, false, Map.of(),
                new NotificationAudience.Everyone());
    }

    private Notification sampleEn(String classification, String message, String classificationEn, String messageEn,
                                  Instant createdAt) {
        return new Notification(UUID.randomUUID(), classification, message, classificationEn, messageEn, "INFO",
                NotificationPriority.NORMAL, null, createdAt, true, false, false, Map.of(),
                new NotificationAudience.Everyone());
    }

    @Test
    void save_kaydeder_ve_ayni_degerleri_dondurur() {
        Notification notification = sample("Kayit Testi", Instant.parse("2026-08-19T08:00:00Z"));

        Notification saved = repository.save(notification);

        assertThat(saved).isEqualTo(notification);
    }

    @Test
    void findById_kaydedilen_bildirimi_bulur() {
        Notification notification = sample("Bul Testi", Instant.parse("2026-08-19T08:00:00Z"));
        repository.save(notification);

        Optional<Notification> found = repository.findById(notification.id());

        assertThat(found).isPresent();
        assertThat(found.get().classification()).isEqualTo("Bul Testi");
    }

    @Test
    void findVisibleBefore_gizli_kayitlari_haric_tutar() {
        Notification gorunur = sample("Gorunur", Instant.parse("2026-08-19T08:00:00Z"));
        Notification gizli = new Notification(UUID.randomUUID(), "Gizli", "mesaj", "INFO",
                NotificationPriority.NORMAL, null, Instant.parse("2026-08-19T08:01:00Z"), false, false, false, Map.of(),
                new NotificationAudience.Everyone());
        repository.save(gorunur);
        repository.save(gizli);

        List<Notification> result = repository.findVisibleBefore(null, 10);

        assertThat(result).extracting(Notification::classification).containsExactly("Gorunur");
    }

    @Test
    void findVisibleBefore_yeniden_eskiye_siralar() {
        repository.save(sample("Ilk", Instant.parse("2026-08-19T08:00:00Z")));
        repository.save(sample("Ikinci", Instant.parse("2026-08-19T08:05:00Z")));
        repository.save(sample("Ucuncu", Instant.parse("2026-08-19T08:10:00Z")));

        List<Notification> result = repository.findVisibleBefore(null, 10);

        assertThat(result).extracting(Notification::classification)
                .containsExactly("Ucuncu", "Ikinci", "Ilk");
    }

    @Test
    void findVisibleBefore_priority_ile_suzer() {
        Notification normal = sample("Normal Bildirim", Instant.parse("2026-08-19T08:00:00Z"));
        Notification yuksek = new Notification(UUID.randomUUID(), "Yuksek Bildirim", "mesaj", "WARNING",
                NotificationPriority.HIGH, null, Instant.parse("2026-08-19T08:01:00Z"), true, false, false, Map.of(),
                new NotificationAudience.Everyone());
        repository.save(normal);
        repository.save(yuksek);

        List<Notification> result = repository.findVisibleBefore(null, 10, NotificationPriority.HIGH);

        assertThat(result).extracting(Notification::classification).containsExactly("Yuksek Bildirim");
    }

    @Test
    void findVisibleSortedByPriority_once_onceliğe_sonra_tarihe_gore_siralar() {
        // Kasitli olarak KARISIK sirayla kaydediyoruz - sonuc created_at'a
        // GORE degil, once oncelige (HIGH -> NORMAL -> LOW), sonra o oncelik
        // icinde tarihe gore (en yeni once) sirali gelmeli.
        repository.save(sample("Eski Yuksek", Instant.parse("2026-08-19T08:00:00Z"), NotificationPriority.HIGH));
        repository.save(sample("En Yeni Dusuk", Instant.parse("2026-08-19T09:00:00Z"), NotificationPriority.LOW));
        repository.save(sample("Yeni Yuksek", Instant.parse("2026-08-19T08:30:00Z"), NotificationPriority.HIGH));
        repository.save(sample("Tek Normal", Instant.parse("2026-08-19T08:15:00Z"), NotificationPriority.NORMAL));

        List<Notification> result = repository.findVisibleSortedByPriority(null, null, null, 10);

        assertThat(result).extracting(Notification::classification)
                .containsExactly("Yeni Yuksek", "Eski Yuksek", "Tek Normal", "En Yeni Dusuk");
    }

    @Test
    void findVisibleSortedByPriority_sayfalama_atlamadan_veya_tekrarlamadan_devam_eder() {
        Notification n1 = repository.save(sample("N1", Instant.parse("2026-08-19T08:00:00Z"), NotificationPriority.HIGH));
        Notification n2 = repository.save(sample("N2", Instant.parse("2026-08-19T08:01:00Z"), NotificationPriority.HIGH));
        Notification n3 = repository.save(sample("N3", Instant.parse("2026-08-19T08:02:00Z"), NotificationPriority.NORMAL));
        Notification n4 = repository.save(sample("N4", Instant.parse("2026-08-19T08:03:00Z"), NotificationPriority.LOW));

        // Tum liste (imlecsiz) referans siralamayi versin.
        List<Notification> full = repository.findVisibleSortedByPriority(null, null, null, 10);
        assertThat(full).hasSize(4);

        // limit=2 ile sayfalayip son kaydin (priority, createdAt, id) ucluсunu
        // imlec olarak vererek devam edelim - birlesik sonuc TAM ayni sirada,
        // atlama/tekrar olmadan gelmeli.
        List<Notification> page1 = repository.findVisibleSortedByPriority(null, null, null, 2);
        assertThat(page1).hasSize(2);
        Notification lastOfPage1 = page1.get(1);

        List<Notification> page2 = repository.findVisibleSortedByPriority(
                lastOfPage1.priority(), lastOfPage1.createdAt(), lastOfPage1.id(), 2);

        assertThat(page1).extracting(Notification::id)
                .containsExactly(full.get(0).id(), full.get(1).id());
        assertThat(page2).extracting(Notification::id)
                .containsExactly(full.get(2).id(), full.get(3).id());
    }

    @Test
    void hide_kaydi_gizler_ama_tabloda_birakir() {
        Notification notification = sample("Gizlenecek", Instant.parse("2026-08-19T08:00:00Z"));
        repository.save(notification);

        boolean changed = repository.hide(notification.id());

        assertThat(changed).isTrue();
        assertThat(repository.findVisibleBefore(null, 10)).isEmpty();
        assertThat(repository.findById(notification.id())).isPresent();
    }

    @Test
    void setSaved_kaydeder_ve_geri_alinabilir() {
        Notification kaydedilecek = sample("Kaydedilecek", Instant.parse("2026-08-19T08:00:00Z"));
        Notification digeri = sample("Diger", Instant.parse("2026-08-19T08:01:00Z"));
        repository.save(kaydedilecek);
        repository.save(digeri);

        boolean changed = repository.setSaved(kaydedilecek.id(), true);
        assertThat(changed).isTrue();

        assertThat(repository.findById(kaydedilecek.id())).get().extracting(Notification::saved).isEqualTo(true);
        assertThat(repository.findSavedBefore(null, 10))
                .extracting(Notification::classification)
                .containsExactly("Kaydedilecek");

        // GERI ALINABILIR - read/hidden'in aksine saved=false ile kaydi kaldirmak mumkun.
        repository.setSaved(kaydedilecek.id(), false);
        assertThat(repository.findSavedBefore(null, 10)).isEmpty();
        assertThat(repository.findById(kaydedilecek.id())).get().extracting(Notification::saved).isEqualTo(false);
    }

    @Test
    void search_baslik_icerik_ve_tipte_buyuk_kucuk_harf_duyarsiz_arar() {
        repository.save(sample("Sunucu Bakimi", Instant.parse("2026-08-19T08:00:00Z")));
        repository.save(sample("Farkli Baslik", Instant.parse("2026-08-19T08:01:00Z")));

        // "mesaj" tum ornek kayitlarin ICERIGINDE oldugu icin bununla arama
        // yapmak yerine baslikta gecen, KUCUK harfli bir sozcuk kullaniyoruz -
        // arama buyuk/kucuk harf duyarsiz olmali.
        List<Notification> result = repository.searchVisibleBefore("bakimi", null, 10);

        assertThat(result).extracting(Notification::classification).containsExactly("Sunucu Bakimi");
    }

    @Test
    void save_ve_findById_ingilizce_metni_korur() {
        Notification withEn = sampleEn("Bakim", "Sistem kapanacak", "Maintenance", "System goes down",
                Instant.parse("2026-08-19T08:00:00Z"));
        Notification withoutEn = sample("Sadece TR", Instant.parse("2026-08-19T08:01:00Z"));
        repository.save(withEn);
        repository.save(withoutEn);

        assertThat(repository.findById(withEn.id())).get().satisfies(n -> {
            assertThat(n.classificationEn()).isEqualTo("Maintenance");
            assertThat(n.messageEn()).isEqualTo("System goes down");
        });
        assertThat(repository.findById(withoutEn.id())).get().satisfies(n -> {
            assertThat(n.classificationEn()).isNull();
            assertThat(n.messageEn()).isNull();
        });
    }

    @Test
    void search_ingilizce_metinde_de_arar() {
        repository.save(sampleEn("Bakim", "Sistem kapanacak", "Maintenance window", "System goes down",
                Instant.parse("2026-08-19T08:00:00Z")));
        repository.save(sample("Alakasiz", Instant.parse("2026-08-19T08:01:00Z")));

        // "maintenance" yalnizca classification_en'de geciyor - arama onu da taramali.
        List<Notification> result = repository.searchVisibleBefore("maintenance", null, 10);

        assertThat(result).extracting(Notification::classification).containsExactly("Bakim");
    }

    @Test
    void search_ozel_like_karakterlerini_harfi_harfine_arar() {
        Notification notification = sample("Yüzde50%_indirim", Instant.parse("2026-08-19T08:00:00Z"));
        repository.save(notification);

        // "%" ve "_" SQL LIKE'ta joker karakter - kullanicinin yazdigi bu
        // karakterler harfi harfine aranmali, tum kayitlari eslestirmemeli.
        List<Notification> result = repository.searchVisibleBefore("50%_indirim", null, 10);

        assertThat(result).extracting(Notification::id).containsExactly(notification.id());
    }
}