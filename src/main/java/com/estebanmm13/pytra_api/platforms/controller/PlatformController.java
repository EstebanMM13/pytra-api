package com.estebanmm13.pytra_api.platforms.controller;

import com.estebanmm13.pytra_api.platforms.dto.PlatformRequestDto;
import com.estebanmm13.pytra_api.platforms.dto.PlatformResponseDto;
import com.estebanmm13.pytra_api.platforms.service.PlatformCreationResult;
import com.estebanmm13.pytra_api.platforms.service.PlatformService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Global platform catalog. POST is create-if-missing: 201 when created, 200 with the existing entry otherwise. */
@RestController
@RequestMapping("/api/v1/platforms")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService platformService;

    @GetMapping
    public ResponseEntity<List<PlatformResponseDto>> findAll() {
        return ResponseEntity.ok(platformService.findAll());
    }

    @PostMapping
    public ResponseEntity<PlatformResponseDto> create(@Valid @RequestBody PlatformRequestDto request) {
        PlatformCreationResult result = platformService.createIfMissing(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.platform());
    }
}
