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
package io.github.bilalefeuysl.notification.rest.error;

import io.github.bilalefeuysl.notification.rest.controller.NotificationController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SADECE kutuphanenin kendi controller'i icin hata cevirici.
 * <p>
 * ONEMLI - {@code assignableTypes} neden zorunlu: {@code @RestControllerAdvice}
 * varsayilan olarak uygulamadaki TUM controller'lara uygulanir. Kapsam
 * verilmeden, asagidaki {@code Exception.class} yakalayicisi kutuphaneyi
 * kullanan uygulamanin KENDI controller'larindan cikan hatalari da yutar ve
 * hepsini bu kutuphanenin Turkce {@code INTERNAL_ERROR} govdesine cevirirdi:
 * uygulamanin kendi {@code @RestControllerAdvice}'i (siralama garantisi
 * olmadigi icin) devre disi kalabilir, Spring Security'nin
 * {@code AccessDeniedException}/{@code AuthenticationException} akisi kesilip
 * 401/403 yerine 500 donulebilirdi. Kapsami kutuphanenin controller'iyla
 * sinirlamak bunu tamamen onler - {@code assignableTypes} alt siniflari da
 * kapsar, yani kendi {@code NotificationController}'ini turetmis uygulamalar
 * da korunur.
 */
@RestControllerAdvice(assignableTypes = NotificationController.class)
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class NotificationRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRestExceptionHandler.class);

    @ExceptionHandler(InvalidNotificationRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidNotificationRequestException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", ex.getMessage()));
    }

    /**
     * Istemcinin gonderdigi degerin okunamadigi/cevrilemedigi durumlar - hepsi
     * 400, kutuphanenin diger hatalariyla AYNI JSON govdesiyle.
     * <p>
     * Neden ayri ayri sayiliyor: bunlarin bir kismi (ozellikle Spring 6.1'de
     * {@code MethodArgumentTypeMismatchException}) {@code ErrorResponse}
     * arayuzunu UYGULAMIYOR, yani asagidaki son care yakalayicinin
     * "Spring'inkileri gecir" kontrolune takilmiyorlar. Acikca ele alinmazlarsa
     * {@code ?before=abc} ya da bozuk bir JSON govdesi gibi TAMAMEN istemci
     * kaynakli hatalar "500 Beklenmeyen bir hata olustu" olarak donerdi -
     * tuketici, kendi isteginin yanlis oldugunu asla anlayamazdi.
     */
    @ExceptionHandler({
            TypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadInput(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", ex.getMessage()));
    }

    /**
     * Son care yakalayici. Spring'in KENDI web hatalarini (400/404/405/415 ...)
     * BILEREK gecirir: bunlarin hepsi {@code org.springframework.web.ErrorResponse}
     * arayuzunu uygular ve dogru HTTP durumunu zaten kendileri tasir. Yakalasaydik,
     * istemcinin duzeltebilecegi bir hata (orn. {@code ?before=abc} - tarihe
     * cevrilemeyen parametre, ya da yanlis HTTP metodu) "500 Beklenmeyen bir hata
     * olustu" olarak donerdi ve hata ayiklamak imkansizlasirdi.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof org.springframework.web.ErrorResponse) {
            throw ex;
        }
        log.error("Beklenmeyen hata", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Beklenmeyen bir hata olustu"));
    }
}
