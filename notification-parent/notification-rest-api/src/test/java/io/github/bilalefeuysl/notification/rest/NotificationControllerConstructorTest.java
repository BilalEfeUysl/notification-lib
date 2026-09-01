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
import io.github.bilalefeuysl.notification.rest.identity.HeaderNotificationIdentityResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Adim 2'de NotificationController kurucusuna eklenen korumalarin testi -
 * yanlis yapilandirmayi (25 Agustos bug'inin bir kok nedeni) CALISMA ANINDA
 * degil, nesne olusturulurken (uygulama acilisinda) yakaladigindan emin olur.
 */
class NotificationControllerConstructorTest {

    @Test
    void targetingEnabledWithoutIdentityResolverThrows() {
        FakeNotificationService service = new FakeNotificationService();

        assertThatThrownBy(() -> new NotificationController(service, 25, 100, true, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NotificationIdentityResolver");
    }

    @Test
    void targetingEnabledWithIdentityResolverDoesNotThrow() {
        FakeNotificationService service = new FakeNotificationService();

        assertThatNoException().isThrownBy(
                () -> new NotificationController(service, 25, 100, true, new HeaderNotificationIdentityResolver()));
    }

    @Test
    void nullNotificationServiceThrows() {
        assertThatThrownBy(() -> new NotificationController(null, 25, 100, false, null))
                .isInstanceOf(NullPointerException.class);
    }
}
