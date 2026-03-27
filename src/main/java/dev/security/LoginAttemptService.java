package dev.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


public class LoginAttemptService {
    

    private static final int MAX_ATTEMPTS = 5;

    private static final long LOCK_TIME_DURATION = TimeUnit.MINUTES.toMillis(15);
    

    private final Map<String, LoginAttemptInfo> ipAttempts = new ConcurrentHashMap<>();

    private final Map<String, LoginAttemptInfo> userAttempts = new ConcurrentHashMap<>();

    public void loginFailed(String ip, String username) {

        updateAttemptInfo(ipAttempts, ip);
        

        if (username != null && !username.isEmpty()) {
            updateAttemptInfo(userAttempts, username);
        }
    }

    public void loginSucceeded(String ip, String username) {

        ipAttempts.remove(ip);
        

        if (username != null && !username.isEmpty()) {
            userAttempts.remove(username);
        }
    }

    public boolean isBlocked(String ip) {
        return checkIfBlocked(ipAttempts, ip);
    }
    public boolean isUserBlocked(String username) {
        return checkIfBlocked(userAttempts, username);
    }
    private void updateAttemptInfo(Map<String, LoginAttemptInfo> attemptsMap, String key) {
        LoginAttemptInfo info = attemptsMap.getOrDefault(key, new LoginAttemptInfo());
        info.incrementAttempts();
        attemptsMap.put(key, info);
    }
    private boolean checkIfBlocked(Map<String, LoginAttemptInfo> attemptsMap, String key) {
        LoginAttemptInfo info = attemptsMap.get(key);
        if (info == null) {
            return false;
        }
        

        if (info.getAttempts() >= MAX_ATTEMPTS) {

            if (System.currentTimeMillis() - info.getLastAttemptTime() > LOCK_TIME_DURATION) {

                attemptsMap.remove(key);
                return false;
            }
            return true;
        }
        
        return false;
    }
    private static class LoginAttemptInfo {
        private int attempts = 0;
        private long lastAttemptTime = System.currentTimeMillis();
        
        public void incrementAttempts() {
            attempts++;
            lastAttemptTime = System.currentTimeMillis();
        }
        
        public int getAttempts() {
            return attempts;
        }
        
        public long getLastAttemptTime() {
            return lastAttemptTime;
        }
    }
}