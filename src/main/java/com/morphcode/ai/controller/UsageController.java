package com.morphcode.ai.controller;

import com.morphcode.ai.dto.subscription.UsageTodayResponse;
import com.morphcode.ai.service.UsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageService usageService;

    @GetMapping("/today")
    public ResponseEntity<UsageTodayResponse> getTodayUsage() {
        Long userId = 1L;
        // return ResponseEntity.ok(usageService.getTodayUsageOfUser(userId));
        return null;
    }

}