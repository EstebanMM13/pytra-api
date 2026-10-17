package com.estebanmm13.pytra_api.covers.controller;

import com.estebanmm13.pytra_api.covers.service.CoverThumbnailService;
import com.estebanmm13.pytra_api.error.GlobalExceptionHandler;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Docker-free controller checks (standalone MockMvc with a fake fetcher: no network). */
class CoverThumbnailControllerTest {

    private static final String COVER_URL =
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/620/library_600x900.jpg";

    private final AtomicInteger fetches = new AtomicInteger();

    private MockMvc mockMvc(CoverThumbnailService service) {
        return MockMvcBuilders.standaloneSetup(new CoverThumbnailController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsCacheableJpeg() throws Exception {
        MockMvc mockMvc = mockMvc(new CoverThumbnailService(uri -> {
            fetches.incrementAndGet();
            return png();
        }));

        mockMvc.perform(get("/api/v1/covers/thumb").param("url", COVER_URL).param("w", "120"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        assertThat(fetches).hasValue(1);
    }

    @Test
    void rejectsDisallowedUrlsAndWidthsWithoutFetching() throws Exception {
        MockMvc mockMvc = mockMvc(new CoverThumbnailService(uri -> {
            fetches.incrementAndGet();
            return png();
        }));

        mockMvc.perform(get("/api/v1/covers/thumb").param("url", "http://127.0.0.1/a.jpg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_COVER_URL"));
        mockMvc.perform(get("/api/v1/covers/thumb"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_COVER_URL"));
        mockMvc.perform(get("/api/v1/covers/thumb").param("url", COVER_URL).param("w", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_COVER_WIDTH"));
        assertThat(fetches).hasValue(0);
    }

    @Test
    void mapsUpstreamFailures() throws Exception {
        mockMvc(new CoverThumbnailService(uri -> {
            throw new ResourceNotFoundException(SteamIntegrationException.COVER_NOT_FOUND);
        }))
                .perform(get("/api/v1/covers/thumb").param("url", COVER_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("COVER_NOT_FOUND"));

        mockMvc(new CoverThumbnailService(uri -> {
            throw SteamIntegrationException.coverUnavailable();
        }))
                .perform(get("/api/v1/covers/thumb").param("url", COVER_URL))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("COVER_UNAVAILABLE"));
    }

    private static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
