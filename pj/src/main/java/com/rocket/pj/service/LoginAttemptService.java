package com.rocket.pj.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Map;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPT = 5;
    private static final long BLOCK_DURATION = TimeUnit.MINUTES.toMillis(15);

    // key: ip, value: attempts
    private final Map<String, Integer> attemptsCache = new ConcurrentHashMap<>();
    // key: ip, value: block expiration timestamp
    private final Map<String, Long> blockCache = new ConcurrentHashMap<>();

    public void loginSucceeded(String key) {
        attemptsCache.remove(key);
        blockCache.remove(key);
    }

    public void loginFailed(String key) {
        int attempts = attemptsCache.getOrDefault(key, 0);
        attempts++;
        attemptsCache.put(key, attempts);

        if (attempts >= MAX_ATTEMPT) {
            blockCache.put(key, System.currentTimeMillis() + BLOCK_DURATION);
        }
    }

    public boolean isBlocked(String key) {
        if (blockCache.containsKey(key)) {
            long expiration = blockCache.get(key);
            if (System.currentTimeMillis() < expiration) {
                return true;
            } else {
                // Block expired
                blockCache.remove(key);
                attemptsCache.remove(key);
                return false;
            }
        }
        return false;
    }
}
