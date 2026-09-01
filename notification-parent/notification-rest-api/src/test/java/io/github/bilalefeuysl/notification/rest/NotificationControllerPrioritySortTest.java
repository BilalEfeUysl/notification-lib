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

import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.rest.controller.NotificationController;
import io.github.bilalefeuysl.notification.rest.error.NotificationRestExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * sort=priority (B11, opt-in oncelik siralamasi) icin AYRI bir Spring context'te
 * calisan test sinifi - NotificationControllerTest ile AYNI context'i (ve dolayisiyla
 * ayni singleton FakeNotificationService'i) paylasmiyor, cunku buradaki testler
 * kendi seed() verisini birikimli ekliyor ve digerlerinin state'ini kirletmemesi
 * icin izole bir context'te calismasi gerekiyor.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = NotificationControllerPrioritySortTest.TestApp.class)
@AutoConfigureMockMvc
// Her test metodu KENDI FakeNotificationService bean'ini alsin diye -
// asagidaki testler birikimli seed() cagirir, ayni context'i paylassalar
// birbirinin state'ini kirletirlerdi (NotificationControllerTest'teki
// gibi "seed edip sonra hide ederek net sifira dondurme" deseni burada
// pratik degil, cunku bazi testler kasitli olarak ilk sayfayi tuketip
// devam ediyor). DIKKAT: classMode gerekiyor - methodMode SADECE metod
// seviyesinde @DirtiesContext'te islevli, sinif seviyesinde sessizce
// yok sayilir.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationControllerPrioritySortTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FakeNotificationService fakeService;

    @Test
    void onceOnceligeSonraTariheGoreSiralar() throws Exception {
        // FakeNotificationService constructor'i zaten 2 NORMAL kayit ekliyor
        // (Basligi 1: 10:00, Basligi 2: 09:00) - uzerine bir HIGH ve bir LOW ekliyoruz.
        fakeService.seed("Yuksek Oncelik", "icerik", Instant.parse("2026-08-17T08:00:00Z"), NotificationPriority.HIGH);
        fakeService.seed("Dusuk Oncelik", "icerik", Instant.parse("2026-08-17T12:00:00Z"), NotificationPriority.LOW);

        mockMvc.perform(get("/api/notifications").param("sort", "priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].classification").value("Yuksek Oncelik"))
                .andExpect(jsonPath("$.items[1].classification").value("Basligi 1"))
                .andExpect(jsonPath("$.items[2].classification").value("Basligi 2"))
                .andExpect(jsonPath("$.items[3].classification").value("Dusuk Oncelik"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void imlecIleSayfalamaAtlamadanDevamEder() throws Exception {
        fakeService.seed("Yuksek Oncelik", "icerik", Instant.parse("2026-08-17T08:00:00Z"), NotificationPriority.HIGH);
        fakeService.seed("Dusuk Oncelik", "icerik", Instant.parse("2026-08-17T12:00:00Z"), NotificationPriority.LOW);

        String firstPageJson = mockMvc.perform(get("/api/notifications").param("sort", "priority").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextPriorityCursor").exists())
                .andReturn().getResponse().getContentAsString();

        String cursor = com.jayway.jsonpath.JsonPath.read(firstPageJson, "$.nextPriorityCursor");

        mockMvc.perform(get("/api/notifications").param("sort", "priority").param("limit", "2")
                        .param("priorityCursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].classification").value("Basligi 2"))
                .andExpect(jsonPath("$.items[1].classification").value("Dusuk Oncelik"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void aramaIleBirlikteKullanilamaz() throws Exception {
        mockMvc.perform(get("/api/notifications").param("sort", "priority").param("q", "test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void gecersizCursorBadRequestDoner() throws Exception {
        mockMvc.perform(get("/api/notifications").param("sort", "priority").param("priorityCursor", "gecersiz!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void sortParametresiVerilmezseEskiDavranisAynenCalisir() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].classification").value("Basligi 1"));
    }

    @SpringBootApplication
    @Import(TestPathPrefixConfig.class)
    static class TestApp {

        @Bean
        FakeNotificationService fakeNotificationService() {
            return new FakeNotificationService();
        }

        @Bean
        NotificationController notificationController(FakeNotificationService service) {
            return new NotificationController(service, 25, 100, false, null);
        }

        @Bean
        NotificationRestExceptionHandler notificationRestExceptionHandler() {
            return new NotificationRestExceptionHandler();
        }
    }
}
