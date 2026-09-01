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

import io.github.bilalefeuysl.notification.core.config.NotificationProperties;
import io.github.bilalefeuysl.notification.core.service.NotificationService;
import io.github.bilalefeuysl.notification.rest.controller.NotificationController;
import io.github.bilalefeuysl.notification.rest.error.NotificationRestExceptionHandler;
import io.github.bilalefeuysl.notification.rest.identity.HeaderNotificationIdentityResolver;
import io.github.bilalefeuysl.notification.rest.identity.NotificationIdentityResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Sadece notification.rest.enabled=false DEGILSE aktif olur (varsayilan: acik).
 */
@Configuration
@ConditionalOnProperty(prefix = "notification.rest", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(DispatcherServlet.class)
public class NotificationRestAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NotificationRestAutoConfiguration.class);

    /**
     * REST tarafi icin varsayilan kimlik cozucu (X-User-Id / X-User-Roles header'larini okur).
     * Kullanan uygulama kendi implementasyonunu tanimlarsa bizimki devre disi kalir.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationIdentityResolver.class)
    public NotificationIdentityResolver notificationRestIdentityResolver() {
        return new HeaderNotificationIdentityResolver();
    }

    @Bean
    @ConditionalOnMissingBean(NotificationController.class)
    public NotificationController notificationController(NotificationService notificationService,
                                                            NotificationProperties properties,
                                                            NotificationIdentityResolver identityResolver) {
        NotificationProperties.Rest rest = properties.getRest();
        return new NotificationController(notificationService, rest.getDefaultLimit(), rest.getMaxLimit(),
                properties.getTargeting().isEnabled(), identityResolver);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationRestExceptionHandler.class)
    public NotificationRestExceptionHandler notificationRestExceptionHandler() {
        return new NotificationRestExceptionHandler();
    }

    /**
     * Capraz-origin (CORS) izni notification.cors.allowed-origins ayarindan gelir.
     * Liste bossa hicbir mapping eklenmez - Spring'in varsayilani gecerli olur
     * (yalnizca ayni-origin). Liste doluysa SADECE o origin'lere, SADECE
     * bildirim REST yoluna (basePath) izin verilir.
     */
    @Bean
    public WebMvcConfigurer notificationCorsConfigurer(NotificationProperties properties) {
        List<String> origins = properties.getCors().getAllowedOrigins();
        String basePath = properties.getRest().getBasePath();
        if (origins == null || origins.isEmpty()) {
            log.info("notification.cors.allowed-origins ayarlanmadi - capraz-origin tarayici istemcileri "
                    + "engellenecek (yalnizca backend ile ayni origin'den sunulan frontend baglanabilir). "
                    + "Frontend farkli bir origin'deyse origin'lerini bu ayara ekleyin.");
            return new WebMvcConfigurer() {
            };
        }
        log.info("notification CORS: su origin'lere izin veriliyor -> {}", origins);
        String[] allowed = origins.toArray(new String[0]);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                for (String pattern : new String[] {basePath, basePath + "/**"}) {
                    registry.addMapping(pattern)
                            .allowedOrigins(allowed)
                            .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                            .allowedHeaders("*")
                            .allowCredentials(true);
                }
            }
        };
    }

    @Bean
    public WebMvcConfigurer notificationRestPathPrefixConfigurer(NotificationProperties properties) {
        String basePath = properties.getRest().getBasePath();
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                configurer.addPathPrefix(basePath, HandlerTypePredicate.forAssignableType(NotificationController.class));
            }
        };
    }
}