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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * {@code notificationFlyway} "bastirici" bean'i SADECE tuketici uygulamanin
 * KENDI Flyway migration'lari YOKKEN kaydedilmeli.
 * <p>
 * Neden: o bean, context'e bir {@link org.flywaydb.core.Flyway} bean'i koyarak
 * Spring Boot'un {@code FlywayAutoConfiguration}'ini (class seviyesinde
 * {@code @ConditionalOnMissingBean(Flyway.class)}) TAMAMEN geri cektirir -
 * migration'lari asil calistiran {@code FlywayMigrationInitializer} dahil.
 * Tuketicinin hic migration'i YOKKEN bu dogru davranistir (aksi halde Boot,
 * bos olmayan bir semada "Found non-empty schema(s) but no schema history table"
 * hatasiyla acilista coker). Ama tuketici klasik Boot yolunu kullaniyorsa
 * ({@code classpath:db/migration} altinda {@code .sql} dosyalari, ayri bir
 * {@code Flyway} bean'i tanimlamadan) bizim bean'imiz onlarin migration'larinin
 * SESSIZCE hic calismamasina yol acardi.
 * <p>
 * Bu kosul, tuketicinin Flyway kullandigina dair bir isaret bulursa
 * {@code false} doner (bean kaydedilmez) - Boot kendi Flyway'ini kurup onlarin
 * migration'larini calistirir; kutuphanenin kendi semasi
 * {@code NotificationSchemaInitializer} tarafindan (ayri gecmis tablolariyla)
 * bagimsiz hazirlanmaya devam eder.
 */
class OnNoConsumerFlywayMigrations implements Condition {

    private static final Logger log = LoggerFactory.getLogger(OnNoConsumerFlywayMigrations.class);

    private static final String[] DEFAULT_MIGRATION_PATTERNS = {
            "classpath*:db/migration/*.sql",
            "classpath*:db/migration/**/*.sql"
    };

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // Tuketici Flyway'i acikca kapattiysa Boot'un config'i zaten devre disi -
        // bastirmaya gerek yok.
        if ("false".equalsIgnoreCase(context.getEnvironment().getProperty("spring.flyway.enabled"))) {
            return false;
        }
        // Tuketici spring.flyway.locations'i acikca ayarladiysa Flyway kullaniyordur.
        if (StringUtils.hasText(context.getEnvironment().getProperty("spring.flyway.locations"))) {
            log.info("notification: spring.flyway.locations ayarli - uygulamanin kendi Flyway'i "
                    + "devrede birakiliyor, kutuphane 'bastirici' Flyway bean'ini kaydetmiyor.");
            return false;
        }
        // Varsayilan konumda (db/migration) tuketiciye ait migration var mi?
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(context.getClassLoader());
        for (String pattern : DEFAULT_MIGRATION_PATTERNS) {
            try {
                Resource[] found = resolver.getResources(pattern);
                if (found.length > 0) {
                    log.info("notification: uygulamanin kendi Flyway migration'lari bulundu "
                            + "(classpath:db/migration) - kutuphane 'bastirici' Flyway bean'ini "
                            + "kaydetmiyor, Boot bu migration'lari normal calistiracak. Kutuphanenin "
                            + "kendi semasi ayri gecmis tablolariyla bagimsiz hazirlanir.");
                    return false;
                }
            } catch (IOException ex) {
                // Tarama basarisiz -> guvenli taraf: eski davranis (bastir).
                log.debug("db/migration taranamadi, bastirici Flyway bean'i kaydedilecek", ex);
            }
        }
        return true;
    }
}
