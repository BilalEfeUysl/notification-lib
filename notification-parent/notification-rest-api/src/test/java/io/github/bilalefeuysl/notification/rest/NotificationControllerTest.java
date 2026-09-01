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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = NotificationControllerTest.TestApp.class)
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FakeNotificationService fakeService;

    @Test
    void listReturnsVisibleNotificationsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].classification").value("Basligi 1"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void hideRemovesNotificationFromList() throws Exception {
        UUID extraId = fakeService.seed("Silinecek", "icerik", java.time.Instant.parse("2026-08-17T11:00:00Z"));

        mockMvc.perform(delete("/api/notifications/{id}", extraId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void invalidLimitReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/notifications").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
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