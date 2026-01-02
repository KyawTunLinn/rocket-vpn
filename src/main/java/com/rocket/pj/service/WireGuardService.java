package com.rocket.pj.service;

import com.rocket.pj.entity.Client;
import com.rocket.pj.entity.SystemConfig;
import com.rocket.pj.repository.ClientRepository;
import com.rocket.pj.repository.SystemConfigRepository;
import com.rocket.pj.util.WgTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WireGuardService {

    @Value("${wg.config.path}")
    private String configPath;

    @Value("${wg.interface}")
    private String wgInterface;

    // Cache fields
    private String cachedDumpOutput;
    private long lastDumpTime = 0;
    private static final long CACHE_DURATION_MS = 3000; // 3 seconds

    private final ClientRepository clientRepository;
    private final SystemConfigRepository systemConfigRepository;

    public WireGuardService(ClientRepository clientRepository, SystemConfigRepository systemConfigRepository) {
        this.clientRepository = clientRepository;
        this.systemConfigRepository = systemConfigRepository;
    }

    private SystemConfig getConfig() {
        return systemConfigRepository.findById(1L).orElse(null);
    }

    public String generatePrivateKey() {
        return WgTool.run("wg genkey").trim();
    }

    public String generatePublicKey(String privateKey) {
        return WgTool.run("echo '" + privateKey + "' | wg pubkey").trim();
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        SystemConfig config = getConfig();
        if (config == null || !config.isSetupComplete()) {
            System.out.println("System not setup yet. Skipping WireGuard initialization.");
            return;
        }

        // Migrate clients if needed
        List<Client> clients = clientRepository.findAll();
        boolean changed = false;
        String clientSubnetPrefix = config.getClientSubnetPrefix();

        for (Client client : clients) {
            if (!client.getAddress().startsWith(clientSubnetPrefix + ".")) {
                String[] parts = client.getAddress().split("\\.");
                if (parts.length == 4) {
                    String newAddress = clientSubnetPrefix + "." + parts[3];
                    client.setAddress(newAddress);
                    clientRepository.save(client);
                    changed = true;
                }
            }
        }

        try {
            syncConfig();
            WgTool.run("wg-quick down " + wgInterface);
            WgTool.run("wg-quick up " + wgInterface);
        } catch (Exception e) {
            try {
                WgTool.run("wg-quick up " + wgInterface);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public String generatePresharedKey() {
        return WgTool.run("wg genpsk").trim();
    }

    public void addClient(String name) {
        SystemConfig config = getConfig();
        if (config == null)
            throw new IllegalStateException("System not setup");

        if (clientRepository.existsByName(name)) {
            throw new IllegalArgumentException("Client with name '" + name + "' already exists.");
        }

        String privateKey = generatePrivateKey();
        String publicKey = generatePublicKey(privateKey);
        String presharedKey = generatePresharedKey();

        long count = clientRepository.count();
        String address = config.getClientSubnetPrefix() + "." + (count + 2) + "/32";

        Client client = new Client();
        client.setName(name);
        client.setPrivateKey(privateKey);
        client.setPublicKey(publicKey);
        client.setPresharedKey(presharedKey);
        client.setAddress(address);

        clientRepository.save(client);
        syncConfig();
    }

    public void firstSetup(String firstClientName) {
        // Ensure server key exists
        getServerPrivateKey();

        // Initialize interface
        init();

        // Add first client
        addClient(firstClientName);
    }

    public void deleteClient(Long id) {
        clientRepository.deleteById(id);
        syncConfig();
    }

    public void toggleClient(Long id) {
        Client client = clientRepository.findById(id).orElseThrow();
        client.setEnabled(!client.isEnabled());
        clientRepository.save(client);
        syncConfig();
    }

    private void syncConfig() {
        SystemConfig config = getConfig();
        if (config == null)
            return;

        StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n");
        sb.append("Address = " + config.getWgInterfaceAddress() + "\n");
        sb.append("SaveConfig = false\n");
        sb.append("ListenPort = " + config.getWgPort() + "\n");
        sb.append("PrivateKey = " + getServerPrivateKey() + "\n");
        sb.append(
                "PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE\n");
        sb.append(
                "PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE\n\n");

        List<Client> clients = clientRepository.findAll();
        for (Client client : clients) {
            if (client.isEnabled()) {
                sb.append("[Peer]\n");
                sb.append("# Name: " + client.getName() + "\n");
                sb.append("PublicKey = " + client.getPublicKey() + "\n");
                sb.append("PresharedKey = " + client.getPresharedKey() + "\n");
                sb.append("AllowedIPs = " + client.getAddress() + "\n\n");
            }
        }

        try {
            Files.write(Paths.get(configPath), sb.toString().getBytes());
            WgTool.run("wg syncconf " + wgInterface + " <(wg-quick strip " + wgInterface + ")");
        } catch (Exception e) {
            try {
                WgTool.run("wg-quick up " + wgInterface);
            } catch (Exception ex) {
                System.err.println("Failed to reload wg: " + ex.getMessage());
            }
        }
    }

    public String getServerPrivateKey() {
        Path keyPath = Paths.get("server_private.key");
        if (Files.exists(keyPath)) {
            try {
                return Files.readString(keyPath).trim();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            String key = generatePrivateKey();
            try {
                Files.writeString(keyPath, key);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return key;
        }
    }

    public void restoreServerPrivateKey(String privateKey) {
        try {
            Files.writeString(Paths.get("server_private.key"), privateKey);
        } catch (IOException e) {
            throw new RuntimeException("Failed to restore server private key", e);
        }
    }

    public void restoreSystemConfig(SystemConfig config) {
        SystemConfig currentFn = getConfig();
        if (currentFn != null) {
            config.setId(currentFn.getId()); // Ensure we overwrite the singleton
        } else {
            config.setId(1L);
        }
        systemConfigRepository.save(config);
    }

    public String getClientConfig(Long id) {
        SystemConfig config = getConfig();
        Client client = clientRepository.findById(id).orElseThrow();
        String serverPublicKey = generatePublicKey(getServerPrivateKey());

        StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n");
        sb.append("PrivateKey = " + client.getPrivateKey() + "\n");
        sb.append("Address = " + client.getAddress() + "\n");
        sb.append("DNS = " + config.getClientDns() + "\n\n");

        sb.append("[Peer]\n");
        sb.append("PublicKey = " + serverPublicKey + "\n");
        sb.append("PresharedKey = " + client.getPresharedKey() + "\n");
        sb.append("AllowedIPs = 0.0.0.0/0\n");

        String endpoint = getPublicIp();
        if (config.getEndpointHost() != null && !config.getEndpointHost().isBlank()) {
            endpoint = config.getEndpointHost();
        }

        sb.append("Endpoint = " + endpoint + ":" + config.getWgPort() + "\n");
        sb.append("PersistentKeepalive = 25\n");

        return sb.toString();
    }

    private String getPublicIp() {
        try {
            return WgTool.run("curl -4 -s ifconfig.me").trim();
        } catch (Exception e) {
            return "YOUR_SERVER_IP";
        }
    }

    private synchronized String getCachedDumpOutput() {
        long now = System.currentTimeMillis();
        if (now - lastDumpTime > CACHE_DURATION_MS || cachedDumpOutput == null) {
            try {
                cachedDumpOutput = WgTool.run("wg show " + wgInterface + " dump");
                lastDumpTime = now;
            } catch (Exception e) {
                cachedDumpOutput = "";
            }
        }
        return cachedDumpOutput;
    }

    public java.util.Map<String, Long> getHandshakeData() {
        try {
            String output = getCachedDumpOutput();
            if (output.isEmpty())
                return java.util.Collections.emptyMap();

            return java.util.Arrays.stream(output.split("\n"))
                    .skip(1)
                    .map(line -> line.split("\t"))
                    .filter(parts -> parts.length > 4)
                    .collect(Collectors.toMap(
                            parts -> parts[0],
                            parts -> Long.parseLong(parts[4])));
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    public java.util.Map<String, String> getDataTransfer() {
        try {
            String output = getCachedDumpOutput();
            if (output.isEmpty())
                return java.util.Collections.emptyMap();

            return java.util.Arrays.stream(output.split("\n"))
                    .skip(1)
                    .map(line -> line.split("\t"))
                    .filter(parts -> parts.length > 6)
                    .collect(Collectors.toMap(
                            parts -> parts[0],
                            parts -> formatBytes(Long.parseLong(parts[5])) + " / "
                                    + formatBytes(Long.parseLong(parts[6]))));
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    public java.util.Map<String, Object> getTotalDataTransfer() {
        long totalRx = 0;
        long totalTx = 0;
        try {
            String output = getCachedDumpOutput();
            if (!output.isEmpty()) {
                for (String line : output.split("\n")) {
                    if (line.isEmpty())
                        continue;
                    String[] parts = line.split("\t");
                    if (parts.length > 6) {
                        // parts[5] = rx (download from client -> server rx)
                        // parts[6] = tx (upload to client -> server tx)
                        totalRx += Long.parseLong(parts[5]);
                        totalTx += Long.parseLong(parts[6]);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        java.util.Map<String, Object> totals = new java.util.HashMap<>();
        totals.put("rx_formatted", formatBytes(totalRx));
        totals.put("tx_formatted", formatBytes(totalTx));
        totals.put("total_formatted", formatBytes(totalRx + totalTx));
        totals.put("rx", totalRx);
        totals.put("tx", totalTx);
        return totals;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public void importClients(List<Client> clients) {
        for (Client client : clients) {
            if (clientRepository.existsByName(client.getName())) {
                throw new IllegalArgumentException(
                        "Import failed: Client name '" + client.getName() + "' already exists.");
            }
            if (clientRepository.findByPublicKey(client.getPublicKey()).isPresent()) {
                throw new IllegalArgumentException(
                        "Import failed: Client public key '" + client.getPublicKey() + "' already exists.");
            }
        }

        for (Client client : clients) {
            client.setId(null);
            if (clientRepository.existsByAddress(client.getAddress())) {
                throw new IllegalArgumentException(
                        "Import failed: Client address '" + client.getAddress() + "' already exists.");
            }
            clientRepository.save(client);
        }
        syncConfig();
    }

    public void resetSystem() {
        try {
            WgTool.run("wg-quick down " + wgInterface);
        } catch (Exception e) {
            // Ignore if already down
        }

        try {
            Files.deleteIfExists(Paths.get(configPath));
            Files.deleteIfExists(Paths.get("server_private.key"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getServerUptime() {
        try {
            return WgTool.run("uptime -p").replace("up ", "");
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public void restartService() {
        try {
            WgTool.run("wg-quick down " + wgInterface);
        } catch (Exception e) {
            // ignore if already down
        }
        try {
            WgTool.run("wg-quick up " + wgInterface);
        } catch (Exception e) {
            throw new RuntimeException("Failed to restart WireGuard: " + e.getMessage());
        }
    }
}
