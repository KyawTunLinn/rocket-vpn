package com.rocket.pj.controller;

import com.rocket.pj.entity.SystemConfig;
import com.rocket.pj.entity.User;
import com.rocket.pj.repository.SystemConfigRepository;
import com.rocket.pj.repository.UserRepository;
import com.rocket.pj.service.WireGuardService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SetupController {

    private final SystemConfigRepository systemConfigRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WireGuardService wireGuardService;

    public SetupController(SystemConfigRepository systemConfigRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, WireGuardService wireGuardService) {
        this.systemConfigRepository = systemConfigRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.wireGuardService = wireGuardService;
    }

    @GetMapping("/setup")
    public String setupPage() {
        if (systemConfigRepository.existsById(1L)) {
            return "redirect:/login";
        }
        return "setup";
    }

    @PostMapping("/setup")
    public String completeSetup(@RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String endpointHost,
            @RequestParam int wgPort,
            @RequestParam String clientSubnetPrefix,
            @RequestParam String clientDns,
            @RequestParam String firstClientName) {

        if (systemConfigRepository.existsById(1L)) {
            return "redirect:/login";
        }

        // 1. Create Admin User
        User admin = new User(username, passwordEncoder.encode(password), "ADMIN");
        userRepository.save(admin);

        // 2. Save System Config
        SystemConfig config = new SystemConfig();
        config.setId(1L);
        config.setEndpointHost(endpointHost);
        config.setWgPort(wgPort);
        config.setClientSubnetPrefix(clientSubnetPrefix);
        config.setClientDns(clientDns);
        config.setWgInterfaceAddress(clientSubnetPrefix + ".1/24"); // Default gateway
        config.setSetupComplete(true);
        systemConfigRepository.save(config);

        // 3. Initialize WireGuard and create first profile
        wireGuardService.firstSetup(firstClientName);

        return "redirect:/login";
    }
}
