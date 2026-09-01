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

import java.util.regex.Pattern;

/**
 * Tablo/sema adlari konfigure edilebilir oldugu icin SQL metnine gomulmek
 * zorunda (bunlar ? ile parametre olarak verilemez). Bu yuzden adlari
 * kullanmadan once dogruluyoruz: SQL injection'a acik kapi birakmiyoruz.
 */
public final class SqlIdentifiers {

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    private SqlIdentifiers() {
    }

    public static String requireSafe(String identifier, String field) {
        if (identifier == null || !SAFE.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    field + " gecersiz: '" + identifier + "'. Sadece harf, rakam ve alt cizgi kullanilabilir "
                            + "ve rakamla baslayamaz.");
        }
        return identifier;
    }

    /** sema.tablo seklinde tam nitelikli ad uretir. */
    public static String qualify(String schema, String table) {
        return requireSafe(schema, "notification.schema") + "." + requireSafe(table, "notification.table-name");
    }
}
