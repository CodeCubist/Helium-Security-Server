package dev.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/server/lua-script")
public class LuaScriptController {

    private static final String DEFAULT_SCRIPT_NAME = "script.lua";
    private static final String SCRIPT_DIR = System.getProperty("user.dir");

    @GetMapping
    public String luaScriptEditor(Model model, Authentication authentication) {
        try {
            File scriptFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME);
            String content = "";

            if (scriptFile.exists()) {
                content = new String(Files.readAllBytes(scriptFile.toPath()));
            } else {
                content = getDefaultLuaTemplate();
                Files.write(scriptFile.toPath(), content.getBytes());
            }

            model.addAttribute("scriptContent", content);
            model.addAttribute("scriptName", DEFAULT_SCRIPT_NAME);
            model.addAttribute("scriptPath", scriptFile.getAbsolutePath());
            model.addAttribute("fileExists", scriptFile.exists());
            model.addAttribute("fileSize", scriptFile.exists() ? scriptFile.length() : 0);
            model.addAttribute("lastModified", scriptFile.exists() ? new Date(scriptFile.lastModified()) : null);

        } catch (Exception e) {
            model.addAttribute("error", "读取脚本文件失败: " + e.getMessage());
        }

        return "server/lua-script";
    }

    @PostMapping("/save")
    @ResponseBody
    public Map<String, Object> saveScript(@RequestParam String content, Authentication authentication) {
        Map<String, Object> result = new HashMap<>();

        try {
            File scriptFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME);

            if (scriptFile.exists()) {
                File backupFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME + ".backup");
                Files.copy(scriptFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            Files.write(scriptFile.toPath(), content.getBytes());

            result.put("success", true);
            result.put("message", "脚本保存成功");
            result.put("fileSize", scriptFile.length());
            result.put("lastModified", scriptFile.lastModified());

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }

        return result;
    }

    @PostMapping("/create")
    @ResponseBody
    public Map<String, Object> createScript(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();

        try {
            File scriptFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME);

            if (scriptFile.exists()) {
                result.put("success", false);
                result.put("message", "脚本文件已存在");
            } else {
                String defaultContent = getDefaultLuaTemplate();
                Files.write(scriptFile.toPath(), defaultContent.getBytes());

                result.put("success", true);
                result.put("message", "脚本文件创建成功");
                result.put("content", defaultContent);
                result.put("fileSize", scriptFile.length());
                result.put("lastModified", scriptFile.lastModified());
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
        }

        return result;
    }

    @PostMapping("/delete")
    @ResponseBody
    public Map<String, Object> deleteScript(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();

        try {
            File scriptFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME);

            if (scriptFile.exists()) {
                File backupFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME + ".deleted." + System.currentTimeMillis());
                Files.copy(scriptFile.toPath(), backupFile.toPath());

                boolean deleted = scriptFile.delete();
                if (deleted) {
                    result.put("success", true);
                    result.put("message", "脚本文件已删除");
                } else {
                    result.put("success", false);
                    result.put("message", "删除文件失败");
                }
            } else {
                result.put("success", false);
                result.put("message", "脚本文件不存在");
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadScript() {
        try {
            File scriptFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME);

            if (!scriptFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(scriptFile);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + DEFAULT_SCRIPT_NAME + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/upload")
    @ResponseBody
    public Map<String, Object> uploadScript(@RequestParam("file") MultipartFile file, Authentication authentication) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "上传的文件为空");
                return result;
            }

            String fileName = file.getOriginalFilename();
            if (fileName != null && !fileName.toLowerCase().endsWith(".lua")) {
                result.put("success", false);
                result.put("message", "只能上传Lua脚本文件(.lua)");
                return result;
            }

            File scriptFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME);
            if (scriptFile.exists()) {
                File backupFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME + ".backup.upload");
                Files.copy(scriptFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            byte[] bytes = file.getBytes();
            Files.write(scriptFile.toPath(), bytes);

            result.put("success", true);
            result.put("message", "文件上传成功");
            result.put("fileSize", scriptFile.length());
            result.put("lastModified", scriptFile.lastModified());
            result.put("content", new String(bytes));

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/info")
    @ResponseBody
    public Map<String, Object> getScriptInfo() {
        Map<String, Object> result = new HashMap<>();

        try {
            File scriptFile = new File(SCRIPT_DIR, DEFAULT_SCRIPT_NAME);

            result.put("exists", scriptFile.exists());
            result.put("name", DEFAULT_SCRIPT_NAME);
            result.put("path", scriptFile.getAbsolutePath());

            if (scriptFile.exists()) {
                result.put("fileSize", scriptFile.length());
                result.put("lastModified", scriptFile.lastModified());
                result.put("readable", scriptFile.canRead());
                result.put("writable", scriptFile.canWrite());

                String content = new String(Files.readAllBytes(scriptFile.toPath()));
                result.put("content", content);
                result.put("lineCount", content.split("\r\n|\r|\n").length);
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return result;
    }

    private String getDefaultLuaTemplate() {
        return "-- Lua 脚本编辑器\n" +
                "-- 这是一个默认的Lua脚本模板\n" +
                "-- 您可以使用以下API函数：\n\n" +
                "-- 服务器控制\n" +
                "function onStart()\n" +
                "    log(\"脚本启动\")\n" +
                "    -- server:startServer()\n" +
                "    -- server:stopServer()\n" +
                "end\n\n" +
                "function onStop()\n" +
                "    log(\"脚本停止\")\n" +
                "end\n\n" +
                "function onReload()\n" +
                "    log(\"脚本重载\")\n" +
                "end\n\n" +
                "function onSchedule()\n" +
                "    -- 每30秒执行一次\n" +
                "    log(\"定时任务执行: \" .. util.getTimestamp())\n" +
                "end\n\n" +
                "-- 用户事件\n" +
                "function onUserLogin(username, ip)\n" +
                "    log(\"用户登录: \" .. username .. \" 来自IP: \" .. ip)\n" +
                "end\n\n" +
                "function onUserRegister(username, ip)\n" +
                "    log(\"用户注册: \" .. username .. \" 来自IP: \" .. ip)\n" +
                "end\n\n" +
                "-- 可用的API：\n" +
                "-- server:startServer() - 启动服务器\n" +
                "-- server:stopServer() - 停止服务器\n" +
                "-- server:reloadUsers() - 重新加载用户\n" +
                "-- server:reloadTokens() - 重新加载令牌\n" +
                "-- server:reloadActivationCodes() - 重新加载激活码\n" +
                "-- server:banIp(ip) - 封禁IP\n" +
                "-- server:isServerRunning() - 检查服务器是否运行\n" +
                "-- server:isWebManagerRunning() - 检查Web管理是否运行\n" +
                "-- server:getUserCount() - 获取用户数量\n" +
                "-- server:getTokenCount() - 获取令牌数量\n" +
                "-- server:getActivationCodeCount() - 获取激活码数量\n" +
                "-- util:sleep(ms) - 休眠指定毫秒\n" +
                "-- util:getTimestamp() - 获取时间戳\n" +
                "-- log(message) - 记录日志\n";
    }
}