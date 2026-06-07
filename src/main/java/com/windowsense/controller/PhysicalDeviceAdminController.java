package com.windowsense.controller;

import com.windowsense.dto.RegisterPhysicalEspTokenRequest;
import com.windowsense.dto.RegisterPhysicalEspTokenOnlyRequest;
import com.windowsense.dto.RegisterPhysicalEspTokenOnlyResponse;
import com.windowsense.dto.RegisterPhysicalEspTokenResponse;
import com.windowsense.service.PhysicalDeviceRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/physical-devices")
public class PhysicalDeviceAdminController {

    private final PhysicalDeviceRegistrationService registrationService;

    public PhysicalDeviceAdminController(PhysicalDeviceRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register-token-device")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterPhysicalEspTokenResponse registerTokenDevice(
            @Valid @RequestBody RegisterPhysicalEspTokenRequest request
    ) {
        return registrationService.registerWithHardcodedToken(request);
    }

    @PostMapping("/register-token")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterPhysicalEspTokenOnlyResponse registerToken(
            @Valid @RequestBody RegisterPhysicalEspTokenOnlyRequest request
    ) {
        return registrationService.registerWithHardcodedTokenOnly(request);
    }
}
