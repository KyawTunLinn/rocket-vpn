package com.rocket.pj.config;

import com.rocket.pj.util.WgTool;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Order(1)
public class AutoInstaller implements CommandLineRunner {

    private static final List<String> REQUIRED_COMMANDS = Arrays.asList(
            "wg", "ip", "qrencode", "iptables", "curl");

    @Override
    public void run(String... args) throws Exception {
        // 1. Check Root Permission
        String uid = WgTool.run("id -u").trim();
        if (!"0".equals(uid)) {
            System.err.println("CRITICAL ERROR: This application must be run as root!");
            System.err.println("Please run with: sudo java -jar ...");
            System.exit(1);
        }

        // 2. Check OS is Ubuntu
        try {
            String osRelease = WgTool.run("cat /etc/os-release").toLowerCase();
            if (!osRelease.contains("ubuntu")) {
                System.err.println("CRITICAL ERROR: This application is designed for Ubuntu Linux only!");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: Could not determine OS version!");
            System.exit(1);
        }

        System.out.println("System checks passed (Root & Ubuntu detected).");
        System.out.println("Checking system dependencies...");

        boolean missingDependencies = false;
        for (String cmd : REQUIRED_COMMANDS) {
            if (!isCommandAvailable(cmd)) {
                System.out.println("Missing dependency: " + cmd);
                missingDependencies = true;
            }
        }

        if (missingDependencies) {
            System.out.println("Installing dependencies... This may take a while.");
            try {
                // Run update and install
                // timeout 600 seconds (10 minutes)
                WgTool.run(
                        "sudo apt-get update && sudo apt-get install -y wireguard wireguard-tools iproute2 qrencode iptables curl",
                        600);
                System.out.println("Dependencies installed successfully.");
            } catch (Exception e) {
                System.err.println("Failed to auto-install dependencies: " + e.getMessage());
                // We don't throw exception here to allow app to start, but it might fail later
            }
        } else {
            System.out.println("All system packages are installed.");
        }

        // Check IP Forwarding
        checkAndEnableIpForwarding();
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            WgTool.run("which " + cmd);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkAndEnableIpForwarding() {
        try {
            String output = WgTool.run("sysctl -n net.ipv4.ip_forward");
            if ("0".equals(output.trim())) {
                System.out.println("Enabling IP Forwarding...");
                WgTool.run("sudo sysctl -w net.ipv4.ip_forward=1");

                // Persist in /etc/sysctl.conf
                try {
                    WgTool.run("grep \"^net.ipv4.ip_forward=1\" /etc/sysctl.conf");
                } catch (Exception e) {
                    // Not found, append it
                    WgTool.run("echo 'net.ipv4.ip_forward=1' | sudo tee -a /etc/sysctl.conf");
                }

                System.out.println("IP Forwarding enabled.");
            } else {
                System.out.println("IP Forwarding is already enabled.");
            }
        } catch (Exception e) {
            System.err.println("Failed to check/enable IP forwarding: " + e.getMessage());
        }
    }
}
