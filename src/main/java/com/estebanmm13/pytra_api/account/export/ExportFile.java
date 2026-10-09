package com.estebanmm13.pytra_api.account.export;

import org.springframework.http.MediaType;

public record ExportFile(String filename, MediaType mediaType, byte[] content) {
}
