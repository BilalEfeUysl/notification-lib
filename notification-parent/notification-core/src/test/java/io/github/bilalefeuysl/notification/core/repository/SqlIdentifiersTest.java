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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlIdentifiersTest {

    @Test
    void requireSafe_gecerli_isimleri_kabul_eder() {
        assertThat(SqlIdentifiers.requireSafe("notifications", "field")).isEqualTo("notifications");
        assertThat(SqlIdentifiers.requireSafe("public", "field")).isEqualTo("public");
        assertThat(SqlIdentifiers.requireSafe("my_table_2", "field")).isEqualTo("my_table_2");
    }

    @Test
    void requireSafe_sql_injection_denemelerini_reddeder() {
        assertThatThrownBy(() -> SqlIdentifiers.requireSafe("notifications; DROP TABLE users;--", "field"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SqlIdentifiers.requireSafe("notifications' OR '1'='1", "field"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireSafe_rakamla_baslayan_ismi_reddeder() {
        assertThatThrownBy(() -> SqlIdentifiers.requireSafe("1notifications", "field"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireSafe_bosluk_veya_ozel_karakter_iceren_ismi_reddeder() {
        assertThatThrownBy(() -> SqlIdentifiers.requireSafe("my table", "field"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifiers.requireSafe("my-table", "field"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireSafe_null_reddeder() {
        assertThatThrownBy(() -> SqlIdentifiers.requireSafe(null, "field"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void qualify_sema_ve_tabloyu_nokta_ile_birlestirir() {
        assertThat(SqlIdentifiers.qualify("public", "notifications")).isEqualTo("public.notifications");
    }
}