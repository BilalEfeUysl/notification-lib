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
import io.github.bilalefeuysl.notification.rest.identity.HeaderNotificationIdentityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * targeting acik senaryosunu kapsar - NotificationControllerTest'teki TestApp
 * targetingEnabled=false, identityResolver=null ile sabit oldugu icin ayri bir
 * Spring context gerekiyor.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = NotificationControllerTargetingTest.TestApp.class)
@AutoConfigureMockMvc
class NotificationControllerTargetingTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void missingIdentityHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void validIdentityHeaderReturnsOk() throws Exception {
        mockMvc.perform(get("/api/notifications").header("X-User-Id", "user1"))
                .andExpect(status().isOk());
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
            return new NotificationController(service, 25, 100, true, new HeaderNotificationIdentityResolver());
        }

        @Bean
        NotificationRestExceptionHandler notificationRestExceptionHandler() {
            return new NotificationRestExceptionHandler();
        }
    }
}
