package com.security.controller.admin;

import com.security.dto.BankTransferSettingsRequest;
import com.security.dto.BankTransferSettingsResponse;
import com.security.security.UserPrincipal;
import com.security.service.BankTransferSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payment-settings/bank-transfer")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SYSTEM_SETTINGS')")
public class BankTransferSettingsController {

    private final BankTransferSettingsService bankTransferSettingsService;

    @GetMapping
    public ResponseEntity<BankTransferSettingsResponse> getSettings() {
        return ResponseEntity.ok(bankTransferSettingsService.getAdminSettings());
    }

    @PutMapping
    public ResponseEntity<BankTransferSettingsResponse> updateSettings(
            @AuthenticationPrincipal UserPrincipal userDetails,
            @Valid @RequestBody BankTransferSettingsRequest request) {
        BankTransferSettingsResponse response = bankTransferSettingsService.updateSettings(userDetails.getId(), request);
        return ResponseEntity.ok(response);
    }
}
