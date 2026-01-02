package com.rocket.pj.controller;

import com.google.zxing.BarcodeFormat;
// import com.google.zxing.client.j2se.MatrixToImageWriter; // Removed
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.rocket.pj.entity.Client;
import com.rocket.pj.repository.ClientRepository;
import com.rocket.pj.service.WireGuardService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.rocket.pj.dto.ExportData;
import com.rocket.pj.entity.SystemConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

@Controller
@org.springframework.aot.hint.annotation.RegisterReflectionForBinding({ ExportData.class, Client.class,
        SystemConfig.class })
public class DashboardController {

    private final ClientRepository clientRepository;
    private final WireGuardService wireGuardService;
    private final com.rocket.pj.repository.UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.rocket.pj.repository.SystemConfigRepository systemConfigRepository;

    public DashboardController(ClientRepository clientRepository, WireGuardService wireGuardService,
            com.rocket.pj.repository.UserRepository userRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            com.rocket.pj.repository.SystemConfigRepository systemConfigRepository) {
        this.clientRepository = clientRepository;
        this.wireGuardService = wireGuardService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemConfigRepository = systemConfigRepository;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<Client> clients = clientRepository.findAll();
        var handshakes = wireGuardService.getHandshakeData();
        var transfer = wireGuardService.getDataTransfer();
        var totalTransfer = wireGuardService.getTotalDataTransfer();

        model.addAttribute("clients", clients);
        model.addAttribute("handshakes", handshakes);
        model.addAttribute("transfer", transfer);
        model.addAttribute("totalTransfer", totalTransfer);
        model.addAttribute("now", System.currentTimeMillis() / 1000);
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/clients/add")
    public String addClient(@RequestParam String name) {
        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException(
                    "Invalid client name. Only alphanumeric characters, hyphens, and underscores are allowed.");
        }
        wireGuardService.addClient(name);
        return "redirect:/";
    }

    @GetMapping("/clients/delete/{id}")
    public String deleteClient(@PathVariable Long id) {
        wireGuardService.deleteClient(id);
        return "redirect:/";
    }

    @GetMapping("/clients/toggle/{id}")
    public String toggleClient(@PathVariable Long id) {
        wireGuardService.toggleClient(id);
        return "redirect:/";
    }

    @GetMapping(value = "/clients/qrcode/{id}", produces = "image/svg+xml") // Changed to SVG
    @ResponseBody
    public byte[] getQrCode(@PathVariable Long id) throws Exception {
        String config = wireGuardService.getClientConfig(id);
        QRCodeWriter barcodeWriter = new QRCodeWriter();
        // Generate BitMatrix
        BitMatrix bitMatrix = barcodeWriter.encode(config, BarcodeFormat.QR_CODE, 200, 200);

        // Convert BitMatrix to SVG String manually (No AWT required)
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(" ").append(height)
                .append("\" stroke=\"none\">");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>");
        sb.append("<path d=\"");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitMatrix.get(x, y)) {
                    sb.append("M").append(x).append(",").append(y).append("h1v1h-1z ");
                }
            }
        }
        sb.append("\" fill=\"#000000\"/></svg>");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/clients/config/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public ResponseEntity<String> downloadConfig(@PathVariable Long id) {
        String config = wireGuardService.getClientConfig(id);
        Client client = clientRepository.findById(id).orElseThrow();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + client.getName() + ".conf\"")
                .body(config);
    }

    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("handshakes", wireGuardService.getHandshakeData());
        status.put("transfer", wireGuardService.getDataTransfer());
        status.put("totalTransfer", wireGuardService.getTotalDataTransfer());
        status.put("uptime", wireGuardService.getServerUptime());
        status.put("now", System.currentTimeMillis() / 1000);
        return status;
    }

    @PostMapping("/admin/export")
    public ResponseEntity<?> exportClients(@RequestParam String password, java.security.Principal principal) {
        var userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("User not found");
        }
        if (!passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return ResponseEntity.badRequest().body("Incorrect password");
        }

        List<Client> clients = clientRepository.findAll();
        SystemConfig config = systemConfigRepository.findById(1L).orElse(null);
        String privateKey = wireGuardService.getServerPrivateKey();

        ExportData exportData = new ExportData(clients, config, privateKey);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"full_system_export.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(exportData);
    }

    @PostMapping("/admin/import")
    public ResponseEntity<String> importClients(@RequestParam("file") MultipartFile file, @RequestParam String password,
            java.security.Principal principal) {
        var userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("User not found");
        }
        if (!passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return ResponseEntity.badRequest().body("Incorrect password");
        }

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

            // Try to parse as ExportData (new format)
            try {
                ExportData exportData = mapper.readValue(content, ExportData.class);

                // 1. Restore Server Private Key
                if (exportData.getServerPrivateKey() != null && !exportData.getServerPrivateKey().isBlank()) {
                    wireGuardService.restoreServerPrivateKey(exportData.getServerPrivateKey());
                }

                // 2. Restore System Config
                if (exportData.getSystemConfig() != null) {
                    wireGuardService.restoreSystemConfig(exportData.getSystemConfig());
                }

                // 3. Restore Clients
                if (exportData.getClients() != null) {
                    wireGuardService.importClients(exportData.getClients());
                }

                return ResponseEntity.ok("Full system import successful");

            } catch (Exception e) {
                // Fallback to legacy format (List<Client>)
                try {
                    List<Client> clients = mapper.readValue(content, new TypeReference<List<Client>>() {
                    });
                    wireGuardService.importClients(clients);
                    return ResponseEntity.ok("Legacy client import successful");
                } catch (Exception ex) {
                    throw new IllegalArgumentException(
                            "Invalid file format. Could not parse as full export or client list.");
                }
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Import failed: " + e.getMessage());
        }
    }

    @PostMapping("/admin/password")
    @ResponseBody
    public ResponseEntity<String> changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
            java.security.Principal principal) {
        var userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("User not found");
        }
        var user = userOpt.get();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body("Incorrect current password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PostMapping("/admin/reset")
    @ResponseBody
    public ResponseEntity<String> resetSystem(@RequestParam String password, java.security.Principal principal) {
        // 1. Verify Password
        var userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("User not found");
        }
        var user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.badRequest().body("Incorrect password");
        }

        // 2. Wipe Config/Files
        wireGuardService.resetSystem();

        // 3. Wipe Database
        clientRepository.deleteAll();
        userRepository.deleteAll();
        systemConfigRepository.deleteAll();

        return ResponseEntity.ok("System reset successfully");
    }

    @PostMapping("/admin/restart")
    @ResponseBody
    public ResponseEntity<String> restartService(@RequestParam String password, java.security.Principal principal) {
        // 1. Verify Password
        var userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("User not found");
        }
        var user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.badRequest().body("Incorrect password");
        }

        // 2. Restart Service
        try {
            wireGuardService.restartService();
            return ResponseEntity.ok("WireGuard service restarted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to restart service: " + e.getMessage());
        }
    }
}
