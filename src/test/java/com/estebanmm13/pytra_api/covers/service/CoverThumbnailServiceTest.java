package com.estebanmm13.pytra_api.covers.service;

import com.estebanmm13.pytra_api.covers.client.CoverImageFetcher;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverThumbnailServiceTest {

    private static final String COVER_URL =
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/620/library_600x900.jpg";

    @Test
    void resizesToTheRequestedWidthKeepingTheAspectRatio() throws IOException {
        CoverThumbnailService service = new CoverThumbnailService(uri -> png(600, 900));

        BufferedImage thumb180 = read(service.thumbnail(COVER_URL, "180"));
        BufferedImage thumb120 = read(service.thumbnail(COVER_URL, "120"));

        assertThat(thumb180.getWidth()).isEqualTo(180);
        assertThat(thumb180.getHeight()).isEqualTo(270);
        assertThat(thumb120.getWidth()).isEqualTo(120);
        assertThat(thumb120.getHeight()).isEqualTo(180);
    }

    @Test
    void defaultsTo180AndNeverUpscales() throws IOException {
        assertThat(read(new CoverThumbnailService(uri -> png(600, 900)).thumbnail(COVER_URL, null)).getWidth())
                .isEqualTo(180);
        BufferedImage small = read(new CoverThumbnailService(uri -> png(100, 150)).thumbnail(COVER_URL, "180"));
        assertThat(small.getWidth()).isEqualTo(100);
        assertThat(small.getHeight()).isEqualTo(150);
    }

    @Test
    void producesJpegFromTransparentPng() throws IOException {
        byte[] jpeg = new CoverThumbnailService(uri -> png(460, 215)).thumbnail(COVER_URL, "120");
        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF);
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
        assertThat(read(jpeg).getHeight()).isEqualTo(56);
    }

    @Test
    void rejectsWidthsOutsideTheAllowedSet() {
        CoverThumbnailService service = new CoverThumbnailService(uri -> png(600, 900));
        for (String width : new String[]{"0", "-1", "121", "4000", "abc", "180.5"}) {
            assertThatThrownBy(() -> service.thumbnail(COVER_URL, width))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage(InvalidRequestException.INVALID_COVER_WIDTH);
        }
    }

    @Test
    void neverFetchesDisallowedUrls() {
        AtomicInteger fetches = new AtomicInteger();
        CoverThumbnailService service = new CoverThumbnailService(uri -> {
            fetches.incrementAndGet();
            return png(10, 10);
        });

        assertThatThrownBy(() -> service.thumbnail("https://169.254.169.254/x.jpg", "180"))
                .hasMessage(InvalidRequestException.INVALID_COVER_URL);
        assertThat(fetches).hasValue(0);
    }

    @Test
    void cachesThumbnailsPerUrlAndWidth() {
        AtomicInteger fetches = new AtomicInteger();
        CoverImageFetcher fetcher = uri -> {
            fetches.incrementAndGet();
            return png(600, 900);
        };
        CoverThumbnailService service = new CoverThumbnailService(fetcher);

        byte[] first = service.thumbnail(COVER_URL, "180");
        byte[] second = service.thumbnail(COVER_URL, "180");
        service.thumbnail(COVER_URL, "120");

        assertThat(second).isSameAs(first);
        assertThat(fetches).hasValue(2);
        assertThat(service.cacheSize()).isEqualTo(2);
    }

    @Test
    void doesNotCacheFailures() {
        AtomicInteger fetches = new AtomicInteger();
        CoverThumbnailService service = new CoverThumbnailService(uri -> {
            fetches.incrementAndGet();
            return "not an image".getBytes();
        });

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.thumbnail(COVER_URL, "180"))
                    .isInstanceOf(SteamIntegrationException.class)
                    .hasMessage(SteamIntegrationException.COVER_UNAVAILABLE);
        }
        assertThat(fetches).hasValue(2);
        assertThat(service.cacheSize()).isZero();
    }

    @Test
    void refusesOversizedSourcesBeforeDecoding() {
        // A 5000x10 PNG is tiny on the wire but exceeds the per-side cap.
        CoverThumbnailService service = new CoverThumbnailService(uri -> png(CoverThumbnailResizer.MAX_SOURCE_DIMENSION + 1, 10));

        assertThatThrownBy(() -> service.thumbnail(COVER_URL, "180"))
                .isInstanceOf(SteamIntegrationException.class)
                .hasMessage(SteamIntegrationException.COVER_UNAVAILABLE);
    }

    @Test
    void cacheIsBoundedByEntryCount() {
        CoverThumbnailService service = new CoverThumbnailService(uri -> png(20, 30));
        for (int i = 0; i < CoverThumbnailService.MAX_CACHE_ENTRIES + 5; i++) {
            service.thumbnail(COVER_URL + "?i=" + i, "120");
        }
        assertThat(service.cacheSize()).isEqualTo(CoverThumbnailService.MAX_CACHE_ENTRIES);
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
