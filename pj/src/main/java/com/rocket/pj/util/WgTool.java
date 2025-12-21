package com.rocket.pj.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WgTool {

    public static String run(String command) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("bash", "-c", command);
            Process process = builder.start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                throw new RuntimeException("Command timed out: " + command);
            }

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            String error;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                error = reader.lines().collect(Collectors.joining("\n"));
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("Command failed: " + command + "\nError: " + error);
            }

            return output;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command: " + command, e);
        }
    }
}
