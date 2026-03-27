package dev;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LuaScriptManager {
    private static LuaScriptManager instance;
    private final ServerUI serverUI;
    private final Globals luaGlobals;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private LuaValue currentScript;
    private File currentScriptFile;

    private static final String LUA_ON_START = "onStart";
    private static final String LUA_ON_STOP = "onStop";
    private static final String LUA_ON_RELOAD = "onReload";
    private static final String LUA_ON_SCHEDULE = "onSchedule";
    private static final String LUA_ON_USER_LOGIN = "onUserLogin";
    private static final String LUA_ON_USER_REGISTER = "onUserRegister";

    
    private static final String DEFAULT_SCRIPT_NAME = "script.lua";

    private LuaScriptManager(ServerUI serverUI) {
        this.serverUI = serverUI;
        this.luaGlobals = JsePlatform.standardGlobals();
        setupLuaEnvironment();
        autoLoadDefaultScript();
    }

    public static LuaScriptManager getInstance(ServerUI serverUI) {
        if (instance == null) {
            synchronized (LuaScriptManager.class) {
                if (instance == null) {
                    instance = new LuaScriptManager(serverUI);
                }
            }
        }
        return instance;
    }

    private void setupLuaEnvironment() {
        
        luaGlobals.set("server", CoerceJavaToLua.coerce(new LuaServerAPI()));
        luaGlobals.set("log", new log_function());
        luaGlobals.set("util", CoerceJavaToLua.coerce(new LuaUtilAPI()));
    }

    /**
     * 自动加载默认脚本
     */
    private void autoLoadDefaultScript() {
        try {
            
            String currentDir = System.getProperty("user.dir");
            File defaultScript = new File(currentDir, DEFAULT_SCRIPT_NAME);

            if (defaultScript.exists() && defaultScript.isFile()) {
                if (loadScript(defaultScript.getAbsolutePath())) {
                    serverUI.log("[LUA] 已自动加载默认脚本: " + DEFAULT_SCRIPT_NAME);
                } else {
                    serverUI.log("[LUA] 自动加载默认脚本失败: " + DEFAULT_SCRIPT_NAME);
                }
            } else {
                serverUI.log("[LUA] 未找到默认脚本文件: " + DEFAULT_SCRIPT_NAME + "，需要手动加载");
            }
        } catch (Exception e) {
            serverUI.log("[LUA] 自动加载默认脚本时出错: " + e.getMessage());
        }
    }

    /**
     * 加载并执行Lua脚本
     */
    public boolean loadScript(String scriptPath) {
        try {
            File scriptFile = new File(scriptPath);
            if (!scriptFile.exists()) {
                serverUI.log("[LUA] 脚本文件不存在: " + scriptPath);
                return false;
            }

            
            stopScript();

            
            currentScriptFile = scriptFile;
            currentScript = luaGlobals.loadfile(scriptPath);
            currentScript.call();

            serverUI.log("[LUA] 脚本加载成功: " + scriptPath);
            return true;
        } catch (Exception e) {
            serverUI.log("[LUA] 脚本加载失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 启动脚本调度器
     */
    public boolean startScript() {
        if (currentScript == null) {
            serverUI.log("[LUA] 没有加载任何脚本");
            return false;
        }

        if (isRunning.get()) {
            serverUI.log("[LUA] 脚本已在运行中");
            return true;
        }

        try {
            
            scheduler = Executors.newScheduledThreadPool(1);
            isRunning.set(true);

            
            callLuaFunction(LUA_ON_START);

            
            scheduler.scheduleAtFixedRate(this::executeScheduledTask, 0, 30, TimeUnit.SECONDS);

            serverUI.log("[LUA] 脚本调度器已启动");
            return true;
        } catch (Exception e) {
            serverUI.log("[LUA] 启动脚本失败: " + e.getMessage());
            stopScript();
            return false;
        }
    }

    /**
     * 停止脚本执行
     */
    public void stopScript() {
        if (!isRunning.get()) {
            return;
        }

        try {
            
            callLuaFunction(LUA_ON_STOP);

            
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                scheduler = null;
            }

            isRunning.set(false);
            serverUI.log("[LUA] 脚本调度器已停止");
        } catch (Exception e) {
            serverUI.log("[LUA] 停止脚本时出错: " + e.getMessage());
        }
    }

    /**
     * 重新加载脚本
     */
    public boolean reloadScript() {
        if (currentScriptFile == null) {
            serverUI.log("[LUA] 没有可重载的脚本");
            return false;
        }

        String scriptPath = currentScriptFile.getAbsolutePath();
        stopScript();
        return loadScript(scriptPath) && startScript();
    }

    /**
     * 执行定时任务
     */
    private void executeScheduledTask() {
        if (!isRunning.get()) {
            return;
        }

        try {
            callLuaFunction(LUA_ON_SCHEDULE);
        } catch (Exception e) {
            serverUI.log("[LUA] 定时任务执行失败: " + e.getMessage());
        }
    }

    /**
     * 调用用户登录事件
     */
    public void onUserLogin(String username, String ip) {
        if (!isRunning.get()) return;

        try {
            LuaValue func = luaGlobals.get(LUA_ON_USER_LOGIN);
            if (func.isfunction()) {
                func.call(LuaValue.valueOf(username), LuaValue.valueOf(ip));
            }
        } catch (Exception e) {
            serverUI.log("[LUA] 用户登录事件处理失败: " + e.getMessage());
        }
    }

    /**
     * 调用用户注册事件
     */
    public void onUserRegister(String username, String ip) {
        if (!isRunning.get()) return;

        try {
            LuaValue func = luaGlobals.get(LUA_ON_USER_REGISTER);
            if (func.isfunction()) {
                func.call(LuaValue.valueOf(username), LuaValue.valueOf(ip));
            }
        } catch (Exception e) {
            serverUI.log("[LUA] 用户注册事件处理失败: " + e.getMessage());
        }
    }

    /**
     * 调用重载事件
     */
    public void onReload() {
        if (!isRunning.get()) return;

        try {
            callLuaFunction(LUA_ON_RELOAD);
        } catch (Exception e) {
            serverUI.log("[LUA] 重载事件处理失败: " + e.getMessage());
        }
    }

    /**
     * 调用Lua函数
     */
    private void callLuaFunction(String functionName) {
        try {
            LuaValue func = luaGlobals.get(functionName);
            if (func.isfunction()) {
                func.call();
            }
        } catch (Exception e) {
            
            if (!e.getMessage().contains("not a function")) {
                serverUI.log("[LUA] 调用函数 " + functionName + " 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取当前状态
     */
    public String getStatus() {
        if (!isRunning.get()) {
            if (currentScriptFile == null) {
                return "未加载脚本";
            } else {
                return "已加载 - " + currentScriptFile.getName() + " (未启动)";
            }
        }
        if (currentScriptFile == null) {
            return "运行中 (无脚本)";
        }
        return "运行中 - " + currentScriptFile.getName();
    }

    /**
     * Lua服务器API - 提供给Lua脚本调用的Java方法
     */
    public class LuaServerAPI {
        public void stopServer() {
            SwingUtilities.invokeLater(() -> serverUI.stopServer());
        }

        public void startServer() {
            SwingUtilities.invokeLater(() -> serverUI.startServer());
        }

        public void reloadUsers() {
            serverUI.loadUsers();
        }

        public void reloadTokens() {
            serverUI.loadTokens();
        }

        public void reloadActivationCodes() {
            serverUI.loadActivationCodes();
        }

        public boolean isServerRunning() {
            return serverUI.isServerRunning();
        }

        public boolean isWebManagerRunning() {
            return serverUI.isWebManagerRunning();
        }

        public int getUserCount() {
            return serverUI.getUsers().size();
        }

        public int getTokenCount() {
            return serverUI.getTokens().size();
        }

        public int getActivationCodeCount() {
            return serverUI.getActivationCodes().size();
        }

        public void banIp(String ip) {
            serverUI.banIp(ip);
        }

        public void unbanIp(String ip) {
            
            
        }
    }

    /**
     * Lua工具API - 工具函数
     */
    public class LuaUtilAPI {
        public void sleep(long milliseconds) {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public String getTimestamp() {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    /**
     * Lua日志函数
     */
    static class log_function extends VarArgFunction {
        @Override
        public Varargs invoke(Varargs args) {
            String message = args.tojstring(1);
            
            System.out.println("[LUA] " + message);
            return NIL;
        }
    }

    
    public boolean isRunning() {
        return isRunning.get();
    }

    public File getCurrentScriptFile() {
        return currentScriptFile;
    }
}