package com.estebanmm13.pytra_api.covers.controller;

import com.estebanmm13.pytra_api.covers.service.CoverThumbnailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Downscaled Steam covers for list views. Public on purpose: {@code <img>} tags can't send the
 * bearer token, and the URL allowlist limits it to Steam's image CDNs.
 *
 * <p>Errors use {@code ApiError.message} as a stable code: 400 INVALID_COVER_URL /
 * INVALID_COVER_WIDTH, 404 COVER_NOT_FOUND, 502 COVER_UNAVAILABLE.
 */
@RestController
@RequestMapping("/api/v1/covers")
@RequiredArgsConstructor
public class CoverThumbnailController {

    private final CoverThumbnailService coverThumbnailService;

    @GetMapping("/thumb")
    public ResponseEntity<byte[]> thumbnail(@RequestParam(name = "url", required = false) String url,
                                            @RequestParam(name = "w", required = false) String width) {
        byte[] jpeg = coverThumbnailService.thumbnail(url, width);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(jpeg);
    }
}
