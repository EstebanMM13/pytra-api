package com.estebanmm13.pytra_api.covers.client;

import java.net.URI;

/** Downloads a source cover image. Abstracted so tests never hit the network. */
public interface CoverImageFetcher {

    /**
     * Returns the raw image bytes of an already validated URL.
     *
     * @throws com.estebanmm13.pytra_api.error.ResourceNotFoundException when the CDN answers 404
     * @throws com.estebanmm13.pytra_api.error.SteamIntegrationException (502 COVER_UNAVAILABLE) on any other failure
     */
    byte[] fetch(URI uri);
}
