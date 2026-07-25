package com.univault.storage.controller;

import com.univault.storage.service.StorageStatsService;
import com.univault.storage.dto.StorageStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/storage/stats")
@RequiredArgsConstructor
public class StorageStatsController {

    private final StorageStatsService storageStatsService;

    @GetMapping
    public ResponseEntity<StorageStatsResponse> getStats(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(storageStatsService.getStats(userId));
    }
}