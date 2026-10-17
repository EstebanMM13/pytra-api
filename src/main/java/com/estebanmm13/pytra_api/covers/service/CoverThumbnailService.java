package com.estebanmm13.pytra_api.covers.service;

import com.estebanmm13.pytra_api.covers.client.CoverImageFetcher;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Small JPEG thumbnails of Steam covers: the web shows covers at ~56 px, but Steam's smallest
 * portrait capsule is 600x900 (~60 KB). Results are kept in a bounded LRU cache (entry count and
 * total bytes), concurrent requests for the same thumbnail share one fetch, and at most
 * {@value #MAX_CONCURRENT_GENERATIONS} thumbnails are generated at once so a burst of distinct
 * URLs can't monopolize CPU or memory. Failures are not cached.
 */
@Service
public class CoverThumbnailService {

    public static final Set<Integer> ALLOWED_WIDTHS = Set.of(120, 180);
    public static final int DEFAULT_WIDTH = 180;

    static final int MAX_CACHE_ENTRIES = 300;
    static final long MAX_CACHE_BYTES = 8L * 1024 * 1024;
    static final int MAX_CONCURRENT_GENERATIONS = 4;
    private static final long GENERATION_SLOT_WAIT_SECONDS = 10;

    private final CoverImageFetcher fetcher;
    private final Map<String, byte[]> cache = new LinkedHashMap<>(64, 0.75f, true);
    private long cachedBytes;
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();
    private final Semaphore generationSlots = new Semaphore(MAX_CONCURRENT_GENERATIONS);

    public CoverThumbnailService(CoverImageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** Validates both inputs (400 INVALID_COVER_URL / INVALID_COVER_WIDTH) and returns the JPEG bytes. */
    public byte[] thumbnail(String url, String width) {
        int targetWidth = parseWidth(width);
        URI uri = CoverUrlPolicy.validate(url);
        String key = targetWidth + "|" + uri.toASCIIString();

        byte[] cached = cacheGet(key);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            return await(existing);
        }
        try {
            byte[] thumbnail = generate(uri, targetWidth);
            cachePut(key, thumbnail);
            mine.complete(thumbnail);
            return thumbnail;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    static int parseWidth(String width) {
        if (width == null || width.isBlank()) {
            return DEFAULT_WIDTH;
        }
        try {
            int parsed = Integer.parseInt(width.trim());
            if (ALLOWED_WIDTHS.contains(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the 400 below
        }
        throw new InvalidRequestException(InvalidRequestException.INVALID_COVER_WIDTH);
    }

    private byte[] generate(URI uri, int targetWidth) {
        boolean acquired;
        try {
            acquired = generationSlots.tryAcquire(GENERATION_SLOT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw SteamIntegrationException.coverUnavailable();
        }
        if (!acquired) {
            throw SteamIntegrationException.coverUnavailable();
        }
        try {
            return CoverThumbnailResizer.resizeToJpeg(fetcher.fetch(uri), targetWidth);
        } finally {
            generationSlots.release();
        }
    }

    private static byte[] await(CompletableFuture<byte[]> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw SteamIntegrationException.coverUnavailable();
        }
    }

    private byte[] cacheGet(String key) {
        synchronized (cache) {
            return cache.get(key);
        }
    }

    private void cachePut(String key, byte[] value) {
        synchronized (cache) {
            byte[] previous = cache.put(key, value);
            if (previous != null) {
                cachedBytes -= previous.length;
            }
            cachedBytes += value.length;
            var eldest = cache.entrySet().iterator();
            while ((cache.size() > MAX_CACHE_ENTRIES || cachedBytes > MAX_CACHE_BYTES) && eldest.hasNext()) {
                cachedBytes -= eldest.next().getValue().length;
                eldest.remove();
            }
        }
    }

    int cacheSize() {
        synchronized (cache) {
            return cache.size();
        }
    }
}
