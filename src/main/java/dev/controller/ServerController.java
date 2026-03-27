package dev.controller;

import dev.ServerUI;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/server")
public class ServerController {

    private ServerUI serverUI = ServerUI.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @GetMapping
    public String index(Model model) {
        boolean isServerRunning = serverUI.isServerRunning();
        boolean isWebServerRunning = serverUI.isWebManagerRunning();
        int webPort = serverUI.getWebPort();

        model.addAttribute("isServerRunning", isServerRunning);
        model.addAttribute("isWebServerRunning", isWebServerRunning);
        model.addAttribute("webPort", webPort);
        return "server/index";
    }

    @PostMapping("/start")
    public void startServer(HttpServletResponse response) throws IOException {
        UserDetails currentUser = getCurrentUser();
        if (currentUser == null || !currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            response.sendRedirect("/server?error=" + java.net.URLEncoder.encode("只有超级管理员可以启动服务器", "UTF-8"));
            return;
        }

        serverUI.startServer();
        response.sendRedirect("/server");
    }

    @PostMapping("/stop")
    public void stopServer(HttpServletResponse response) throws IOException {
        UserDetails currentUser = getCurrentUser();
        if (currentUser == null || !currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            response.sendRedirect("/server?error=" + java.net.URLEncoder.encode("只有超级管理员可以停止服务器", "UTF-8"));
            return;
        }

        serverUI.stopServer();
        response.sendRedirect("/server");
    }

    private UserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            return (UserDetails) auth.getPrincipal();
        }
        return null;
    }

    @GetMapping("/users")
    public String users(Model model) {
        
        List<Map<String, Object>> users = new ArrayList<>();
        try {
            
            java.lang.reflect.Field dbManagerField = ServerUI.class.getDeclaredField("databaseManager");
            dbManagerField.setAccessible(true);
            Object dbManager = dbManagerField.get(serverUI);

            
            java.lang.reflect.Method getAllUsersMethod = dbManager.getClass().getMethod("getAllUsers");
            List<?> userList = (List<?>) getAllUsersMethod.invoke(dbManager);

            for (Object user : userList) {
                Map<String, Object> userMap = new HashMap<>();

                java.lang.reflect.Field usernameField = user.getClass().getDeclaredField("username");
                usernameField.setAccessible(true);
                String username = (String) usernameField.get(user);

                java.lang.reflect.Field emailField = user.getClass().getDeclaredField("email");
                emailField.setAccessible(true);
                String email = (String) emailField.get(user);

                java.lang.reflect.Field hwidField = user.getClass().getDeclaredField("hwid");
                hwidField.setAccessible(true);
                String hwid = (String) hwidField.get(user);

                java.lang.reflect.Field currentParamField = user.getClass().getDeclaredField("currentParam");
                currentParamField.setAccessible(true);
                String currentParam = (String) currentParamField.get(user);

                java.lang.reflect.Field tokenCodeField = user.getClass().getDeclaredField("tokenCode");
                tokenCodeField.setAccessible(true);
                String tokenCode = (String) tokenCodeField.get(user);

                userMap.put("id", username);
                userMap.put("username", username);
                userMap.put("email", email);
                userMap.put("hwid", hwid);
                userMap.put("currentParam", currentParam);
                userMap.put("tokenCode", tokenCode);
                userMap.put("registerTime", dateFormat.format(new Date()));

                users.add(userMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
            
            try {
                List<?> userList = serverUI.getUsers();
                for (Object user : userList) {
                    Map<String, Object> userMap = new HashMap<>();

                    java.lang.reflect.Field usernameField = user.getClass().getDeclaredField("username");
                    usernameField.setAccessible(true);
                    String username = (String) usernameField.get(user);

                    java.lang.reflect.Field emailField = user.getClass().getDeclaredField("email");
                    emailField.setAccessible(true);
                    String email = (String) emailField.get(user);

                    java.lang.reflect.Field hwidField = user.getClass().getDeclaredField("hwid");
                    hwidField.setAccessible(true);
                    String hwid = (String) hwidField.get(user);

                    java.lang.reflect.Field currentParamField = user.getClass().getDeclaredField("currentParam");
                    currentParamField.setAccessible(true);
                    String currentParam = (String) currentParamField.get(user);

                    java.lang.reflect.Field tokenCodeField = user.getClass().getDeclaredField("tokenCode");
                    tokenCodeField.setAccessible(true);
                    String tokenCode = (String) tokenCodeField.get(user);

                    userMap.put("id", username);
                    userMap.put("username", username);
                    userMap.put("email", email);
                    userMap.put("hwid", hwid);
                    userMap.put("currentParam", currentParam);
                    userMap.put("tokenCode", tokenCode);
                    userMap.put("registerTime", dateFormat.format(new Date()));

                    users.add(userMap);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        model.addAttribute("users", users);
        UserDetails currentUser = getCurrentUser();
        model.addAttribute("currentUser", currentUser);
        return "server/users";
    }

    @GetMapping("/tokens")
    public String tokens(Model model) {
        List<?> tokenList = serverUI.getTokens();
        List<Map<String, Object>> tokens = new ArrayList<>();

        for (Object token : tokenList) {
            try {
                Map<String, Object> tokenMap = new HashMap<>();

                java.lang.reflect.Field idField = token.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                String id = (String) idField.get(token);

                java.lang.reflect.Field nameField = token.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                String name = (String) nameField.get(token);

                java.lang.reflect.Field descriptionField = token.getClass().getDeclaredField("description");
                descriptionField.setAccessible(true);
                String description = (String) descriptionField.get(token);

                java.lang.reflect.Field createdAtField = token.getClass().getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                long createdAt = (long) createdAtField.get(token);

                tokenMap.put("id", id);
                tokenMap.put("name", name);
                tokenMap.put("description", description);
                tokenMap.put("createdAt", createdAt);

                String formattedCreatedAt = dateFormat.format(new Date(createdAt));
                tokenMap.put("createdAtFormatted", formattedCreatedAt);

                tokens.add(tokenMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        model.addAttribute("tokens", tokens);
        return "server/tokens";
    }

    @GetMapping("/activation-codes")
    public String activationCodes(Model model) {
        List<?> activationCodeList = serverUI.getActivationCodes();
        List<Map<String, Object>> activationCodes = new ArrayList<>();

        for (Object code : activationCodeList) {
            try {
                Map<String, Object> codeMap = new HashMap<>();

                java.lang.reflect.Field codeField = code.getClass().getDeclaredField("code");
                codeField.setAccessible(true);
                String codeStr = (String) codeField.get(code);

                java.lang.reflect.Field tokenIdField = code.getClass().getDeclaredField("tokenId");
                tokenIdField.setAccessible(true);
                String tokenId = (String) tokenIdField.get(code);

                java.lang.reflect.Field usedField = code.getClass().getDeclaredField("used");
                usedField.setAccessible(true);
                boolean used = (boolean) usedField.get(code);

                java.lang.reflect.Field createdAtField = code.getClass().getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                long createdAt = (long) createdAtField.get(code);

                java.lang.reflect.Field usedAtField = code.getClass().getDeclaredField("usedAt");
                usedAtField.setAccessible(true);
                long usedAt = (long) usedAtField.get(code);

                java.lang.reflect.Field usedByField = code.getClass().getDeclaredField("usedBy");
                usedByField.setAccessible(true);
                String usedBy = (String) usedByField.get(code);

                codeMap.put("code", codeStr);
                codeMap.put("tokenId", tokenId);
                codeMap.put("used", used);
                codeMap.put("createdAt", createdAt);
                codeMap.put("usedAt", usedAt);
                codeMap.put("usedBy", usedBy);

                String formattedCreatedAt = dateFormat.format(new Date(createdAt));
                String formattedUsedAt = usedAt > 0 ? dateFormat.format(new Date(usedAt)) : "-";

                codeMap.put("createdAtFormatted", formattedCreatedAt);
                codeMap.put("usedAtFormatted", formattedUsedAt);

                activationCodes.add(codeMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        model.addAttribute("activationCodes", activationCodes);

        List<?> tokenList = serverUI.getTokens();
        List<Map<String, Object>> tokens = new ArrayList<>();

        for (Object token : tokenList) {
            try {
                Map<String, Object> tokenMap = new HashMap<>();

                java.lang.reflect.Field idField = token.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                String id = (String) idField.get(token);

                java.lang.reflect.Field nameField = token.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                String name = (String) nameField.get(token);

                tokenMap.put("id", id);
                tokenMap.put("name", name);
                tokens.add(tokenMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        model.addAttribute("tokens", tokens);
        return "server/activation-codes";
    }

    @GetMapping("/logs")
    public String logs(Model model, HttpServletResponse response) throws IOException {
        UserDetails currentUser = getCurrentUser();
        if (currentUser == null || !currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            response.sendRedirect("/server?error=" + java.net.URLEncoder.encode("只有超级管理员可以查看日志", "UTF-8"));
            return null;
        }

        List<ServerUI.LogEntry> logEntries = serverUI.getLogs();
        List<Map<String, Object>> logs = new ArrayList<>();

        for (ServerUI.LogEntry entry : logEntries) {
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("time", entry.getTime());
            logMap.put("level", entry.getLevel());
            logMap.put("message", entry.getMessage());
            logs.add(logMap);
        }

        model.addAttribute("logs", logs);
        return "server/logs";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam("username") String username, HttpServletResponse response) throws IOException {
        try {
            UserDetails currentUser = getCurrentUser();
            if (currentUser == null || !currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                response.sendRedirect("/server/users?error=" + java.net.URLEncoder.encode("只有超级管理员可以删除用户", "UTF-8"));
                return null;
            }

            
            java.lang.reflect.Field dbManagerField = ServerUI.class.getDeclaredField("databaseManager");
            dbManagerField.setAccessible(true);
            Object dbManager = dbManagerField.get(serverUI);

            java.lang.reflect.Method deleteUserMethod = dbManager.getClass().getMethod("deleteUser", String.class);
            boolean success = (boolean) deleteUserMethod.invoke(dbManager, username);

            if (success) {
                
                serverUI.log("网页管理删除用户: " + username + " (操作员: " +
                        (currentUser != null ? currentUser.getUsername() : "未知") + ")");

                response.sendRedirect("/server/users?success=" + java.net.URLEncoder.encode("用户删除成功", "UTF-8"));
            } else {
                response.sendRedirect("/server/users?error=" + java.net.URLEncoder.encode("用户删除失败", "UTF-8"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/server/users?error=" + java.net.URLEncoder.encode("删除用户时发生错误", "UTF-8"));
        }
        return null;
    }


    @PostMapping("/users/create-web-admin")
    public void createWebAdmin(@RequestParam String username,
                               @RequestParam String hwid,
                               @RequestParam String password,
                               HttpServletResponse response) throws IOException {
        try {
            UserDetails currentUser = getCurrentUser();
            if (currentUser == null || !currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                response.sendRedirect("/server/users?error=" + java.net.URLEncoder.encode("只有超级管理员可以创建用户", "UTF-8"));
                return;
            }

            
            java.lang.reflect.Field dbManagerField = ServerUI.class.getDeclaredField("databaseManager");
            dbManagerField.setAccessible(true);
            Object dbManager = dbManagerField.get(serverUI);

            
            java.lang.reflect.Method getUserMethod = dbManager.getClass().getMethod("getUserByUsername", String.class);
            Object existingUser = getUserMethod.invoke(dbManager, username);

            if (existingUser != null) {
                response.sendRedirect("/server/users?error=" + java.net.URLEncoder.encode("用户名已存在", "UTF-8"));
                return;
            }

            
            Class<?> userClass = Class.forName("dev.User");
            Object user = userClass.getConstructor(String.class, String.class, String.class).newInstance(username, "", password);

            
            java.lang.reflect.Method setHwidMethod = userClass.getMethod("setHwid", String.class);
            setHwidMethod.invoke(user, hwid);

            
            java.lang.reflect.Method setTokenCodeMethod = userClass.getMethod("setTokenCode", String.class);
            setTokenCodeMethod.invoke(user, "public");

            
            java.lang.reflect.Method setCurrentParamMethod = userClass.getMethod("setCurrentParam", String.class);
            setCurrentParamMethod.invoke(user, "1_mode");

            
            java.lang.reflect.Method setRoleMethod = userClass.getMethod("setRole", String.class);
            setRoleMethod.invoke(user, "ROLE_USER");

            
            java.lang.reflect.Method addUserMethod = dbManager.getClass().getMethod("addUser", userClass);
            boolean success = (boolean) addUserMethod.invoke(dbManager, user);

            if (success) {
                
                java.lang.reflect.Method loadUsersMethod = ServerUI.class.getDeclaredMethod("loadUsers");
                loadUsersMethod.setAccessible(true);
                loadUsersMethod.invoke(serverUI);

                
                serverUI.log("网页管理创建用户: " + username + " (操作员: " +
                        (currentUser != null ? currentUser.getUsername() : "未知") + ")");

                response.sendRedirect("/server/users?success=" + java.net.URLEncoder.encode("用户创建成功", "UTF-8"));
            } else {
                response.sendRedirect("/server/users?error=" + java.net.URLEncoder.encode("用户创建失败", "UTF-8"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/server/users?error=" + java.net.URLEncoder.encode("创建用户时发生错误: " + e.getMessage(), "UTF-8"));
        }
    }
    @GetMapping("/performance")
    @ResponseBody
    public Map<String, Object> getPerformance() {
        Map<String, Object> performance = new HashMap<>();

        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        performance.put("totalMemory", totalMemory);
        performance.put("usedMemory", usedMemory);
        performance.put("freeMemory", freeMemory);
        performance.put("maxMemory", maxMemory);

        
        double memoryUsage = (double) usedMemory / totalMemory * 100;
        performance.put("memoryUsage", Math.round(memoryUsage * 100.0) / 100.0);

        
        ThreadGroup parentThreadGroup = Thread.currentThread().getThreadGroup();
        while (parentThreadGroup.getParent() != null) {
            parentThreadGroup = parentThreadGroup.getParent();
        }
        performance.put("threadCount", parentThreadGroup.activeCount());

        
        performance.put("onlineUsers", 0);

        return performance;
    }


    @PostMapping("/tokens/delete")
    public String deleteToken(@RequestParam("tokenId") String tokenId, HttpServletResponse response) throws IOException {
        try {
            UserDetails currentUser = getCurrentUser();
            if (currentUser == null || !currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                response.sendRedirect("/server/tokens?error=" + java.net.URLEncoder.encode("只有超级管理员可以删除令牌", "UTF-8"));
                return null;
            }

            java.lang.reflect.Field tokensField = ServerUI.class.getDeclaredField("tokens");
            tokensField.setAccessible(true);
            java.util.Map<String, Object> tokens = (java.util.Map<String, Object>) tokensField.get(serverUI);

            if (tokens.containsKey(tokenId)) {
                tokens.remove(tokenId);

                java.lang.reflect.Method saveTokensMethod = ServerUI.class.getDeclaredMethod("saveTokens");
                saveTokensMethod.setAccessible(true);
                saveTokensMethod.invoke(serverUI);

                java.lang.reflect.Method updateTokenTableMethod = ServerUI.class.getDeclaredMethod("updateTokenTable");
                updateTokenTableMethod.setAccessible(true);
                updateTokenTableMethod.invoke(serverUI);

                response.sendRedirect("/server/tokens?success=" + java.net.URLEncoder.encode("令牌删除成功", "UTF-8"));
            } else {
                response.sendRedirect("/server/tokens?error=" + java.net.URLEncoder.encode("令牌不存在", "UTF-8"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/server/tokens?error=" + java.net.URLEncoder.encode("删除令牌时发生错误", "UTF-8"));
        }
        return null;
    }

    @PostMapping("/activation-codes/delete")
    public String deleteActivationCode(@RequestParam("code") String code, HttpServletResponse response) throws IOException {
        try {
            java.lang.reflect.Field activationCodesField = ServerUI.class.getDeclaredField("activationCodes");
            activationCodesField.setAccessible(true);
            java.util.Map<String, Object> activationCodes = (java.util.Map<String, Object>) activationCodesField.get(serverUI);

            if (activationCodes.containsKey(code)) {
                activationCodes.remove(code);

                java.lang.reflect.Method saveActivationCodesMethod = ServerUI.class.getDeclaredMethod("saveActivationCodes");
                saveActivationCodesMethod.setAccessible(true);
                saveActivationCodesMethod.invoke(serverUI);

                java.lang.reflect.Method updateActivationTableMethod = ServerUI.class.getDeclaredMethod("updateActivationTable");
                updateActivationTableMethod.setAccessible(true);
                updateActivationTableMethod.invoke(serverUI);

                response.sendRedirect("/server/activation-codes?success=" + java.net.URLEncoder.encode("激活码删除成功", "UTF-8"));
            } else {
                response.sendRedirect("/server/activation-codes?error=" + java.net.URLEncoder.encode("激活码不存在", "UTF-8"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/server/activation-codes?error=" + java.net.URLEncoder.encode("删除激活码时发生错误", "UTF-8"));
        }
        return null;
    }
}