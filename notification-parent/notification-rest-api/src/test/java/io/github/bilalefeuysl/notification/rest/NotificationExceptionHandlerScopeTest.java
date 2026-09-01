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
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kutuphanenin hata cevirici'sinin KENDI controller'iyla SINIRLI kaldigini
 * dogrular.
 * <p>
 * Regresyon koruma: {@link NotificationRestExceptionHandler} bir zamanlar
 * kapsamsiz {@code @RestControllerAdvice} idi ve {@code @ExceptionHandler(Exception.class)}
 * tasiyordu - bu haliyle kutuphaneyi kullanan uygulamanin KENDI
 * controller'larindan cikan hatalari da yutup hepsini kutuphanenin Turkce
 * "INTERNAL_ERROR" govdesine ceviriyordu. {@code assignableTypes} kapsamlamasi
 * kaldirilirsa asagidaki ilk test kirmizi yanar.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = NotificationExceptionHandlerScopeTest.TestApp.class)
@AutoConfigureMockMvc
class NotificationExceptionHandlerScopeTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void libraryAdviceDoesNotSwallowHostApplicationExceptions() throws Exception {
        // Uygulamanin KENDI controller'i patliyor -> uygulamanin KENDI advice'i
        // cevaplamali, kutuphaneninki degil.
        mockMvc.perform(get("/app/boom"))
                .andExpect(status().is(418))
                .andExpect(content().string("uygulamanin kendi hata ceviricisi"));
    }

    @Test
    void libraryAdviceStillHandlesItsOwnController() throws Exception {
        mockMvc.perform(get("/api/notifications").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * Istemcinin duzeltebilecegi bir hata (tarihe cevrilemeyen {@code before})
     * 500 DEGIL 400 donmeli - kutuphanenin son care yakalayicisi Spring'in
     * kendi web hatalarini gecirmeli.
     */
    @Test
    void malformedParameterYieldsBadRequestNotInternalError() throws Exception {
        mockMvc.perform(get("/api/notifications").param("before", "bu-bir-tarih-degil"))
                .andExpect(status().isBadRequest());
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

        @Bean
        HostController hostController() {
            return new HostController();
        }

        @Bean
        HostExceptionHandler hostExceptionHandler() {
            return new HostExceptionHandler();
        }
    }

    /** Kutuphaneyi kullanan uygulamanin kendi controller'ini temsil eder. */
    @RestController
    static class HostController {

        @GetMapping("/app/boom")
        String boom() {
            throw new IllegalStateException("uygulamanin kendi hatasi");
        }
    }

    /** Kutuphaneyi kullanan uygulamanin kendi hata ceviricisini temsil eder. */
    @RestControllerAdvice(assignableTypes = HostController.class)
    static class HostExceptionHandler {

        @ExceptionHandler(IllegalStateException.class)
        ResponseEntity<String> handle(IllegalStateException ex) {
            return ResponseEntity.status(418).body("uygulamanin kendi hata ceviricisi");
        }
    }
}
