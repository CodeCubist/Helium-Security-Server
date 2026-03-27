package dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.jpower.kcp.netty.UkcpChannel;
import io.jpower.kcp.netty.UkcpServerChannel;
import io.netty.bootstrap.UkcpServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.PrivateKey;

public class ServerUI extends JFrame {
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private boolean isRunning = false;
    private JButton startWebButton;
    private JButton stopWebButton;
    private EventLoopGroup group;
    private static final int KCP_PORT = 8888;
    private static final int TCP_PORT = 25580;
    private final int webPort = 443;
    private final WebManager webManager;
    private BuiltInBrowserManager browserManager;
    private boolean isWebManagerRunning = false;
    private static volatile ServerUI instance;
    private final List<LogEntry> logs = new ArrayList<>();
    private static final String USERS_FILE = "users.json";
    private static final String BANNED_IPS_FILE = "banned_ips.json";
    private static final String TOKENS_FILE = "tokens.json";
    private static final String ACTIVATION_CODES_FILE = "activation_codes.json";
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Thread tcpServerThread;
    private volatile boolean tcpRunning = false;
    private final Map<String, String> hwidToParamMap = new HashMap<>();
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();
    private DefaultTableModel tokenTableModel;
    private JTextField newTokenNameField;
    private JTextField newTokenDescField;
    private final Map<String, ActivationCode> activationCodes = new ConcurrentHashMap<>();
    private DefaultTableModel activationTableModel;
    private JComboBox<String> tokenComboBox;
    private JTextField activationCountField;
    private Map<String, Integer> ipRegisterCount = new ConcurrentHashMap<>();
    private Map<String, List<Long>> ipRegisterTimestamps = new ConcurrentHashMap<>();
    private Set<String> bannedIps = ConcurrentHashMap.newKeySet();
    private Map<String, Integer> bannedIpAttempts = new ConcurrentHashMap<>();
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private final DatabaseManager databaseManager = DatabaseManager.getInstance();

    private KeyPair serverRsaKeyPair;
    private final Map<Channel, PublicKey> clientPublicKeys = new ConcurrentHashMap<>();
    private static final String RSA_ALGORITHM = "RSA";
    private static final int RSA_KEY_SIZE = 2048;
    private final LuaScriptManager luaScriptManager;
    private JButton luaStartButton;
    private JButton luaStopButton;
    private JButton luaReloadButton;
    private JButton luaLoadButton;
    private JLabel luaStatusLabel;
    private DataBackupManager backupManager;
    private JButton backupButton;

    public boolean isServerRunning() {
        return isRunning;
    }

    public boolean isWebManagerRunning() {
        return isWebManagerRunning;
    }

    public int getWebPort() {
        return webPort;
    }

    public List<User> getUsers() {
        return new ArrayList<>(users.values());
    }

    public List<Token> getTokens() {
        return new ArrayList<>(tokens.values());
    }

    public List<ActivationCode> getActivationCodes() {
        return new ArrayList<>(activationCodes.values());
    }

    public static class LogEntry {
        private final String time;
        private final String level;
        private final String message;

        public LogEntry(String time, String level, String message) {
            this.time = time;
            this.level = level;
            this.message = message;
        }

        public String getTime() {
            return time;
        }

        public String getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }
    }

    private ServerUI() {
        setTitle("Helium 服务端 - ProMax Plus Boost");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        this.luaScriptManager = LuaScriptManager.getInstance(this);
        this.backupManager = DataBackupManager.getInstance(this);

        try {
            this.serverRsaKeyPair = generateRSAKeyPair();
            log("服务器RSA密钥对生成成功");
        } catch (Exception e) {
            log("生成服务器RSA密钥对失败: " + e.getMessage());
        }

        log("正在初始化数据库...");

        loadUsers();
        loadTokens();
        loadActivationCodes();
        loadBannedIps();
        createUI();
        webManager = WebManager.getInstance();
        webManager.setWebPort(webPort);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (browserManager != null) {
                    browserManager.shutdown();
                }
                if (databaseManager != null) {
                    databaseManager.close();
                }
            }
        });
    }

    public static ServerUI getInstance() {
        if (instance == null) {
            synchronized (ServerUI.class) {
                if (instance == null) {
                    instance = new ServerUI();
                }
            }
        }
        return instance;
    }

    private KeyPair generateRSAKeyPair() throws Exception {
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance(RSA_ALGORITHM);
        keyGen.initialize(RSA_KEY_SIZE);
        return keyGen.generateKeyPair();
    }

    private String encryptRSA(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        int maxLength = RSA_KEY_SIZE / 8 - 11;
        int dataLength = dataBytes.length;

        if (dataLength <= maxLength) {
            byte[] encryptedBytes = cipher.doFinal(dataBytes);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } else {
            StringBuilder encryptedData = new StringBuilder();
            int offset = 0;
            while (offset < dataLength) {
                int length = Math.min(maxLength, dataLength - offset);
                byte[] segment = new byte[length];
                System.arraycopy(dataBytes, offset, segment, 0, length);
                byte[] encryptedSegment = cipher.doFinal(segment);
                encryptedData.append(Base64.getEncoder().encodeToString(encryptedSegment));
                if (offset + length < dataLength) {
                    encryptedData.append("|");
                }
                offset += length;
            }
            return encryptedData.toString();
        }
    }

    private String decryptRSA(String encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        if (encryptedData.contains("|")) {
            StringBuilder decryptedData = new StringBuilder();
            String[] segments = encryptedData.split("\\|");
            for (String segment : segments) {
                byte[] encryptedBytes = Base64.getDecoder().decode(segment);
                byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
                decryptedData.append(new String(decryptedBytes, StandardCharsets.UTF_8));
            }
            return decryptedData.toString();
        } else {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        }
    }

    private String publicKeyToString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    private PublicKey stringToPublicKey(String keyStr) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyStr);
        java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(keyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance(RSA_ALGORITHM);
        return keyFactory.generatePublic(keySpec);
    }

    private String encryptAES(String data, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log("AES加密失败: " + e.getMessage());
            return null;
        }
    }

    private String generateAESKey() {
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[16];
        random.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    void loadTokens() {
        try {
            if (Files.exists(Paths.get(TOKENS_FILE))) {
                String json = new String(Files.readAllBytes(Paths.get(TOKENS_FILE)));
                List<Token> tokenList = gson.fromJson(json, new TypeToken<List<Token>>(){}.getType());
                if (tokenList != null) {
                    for (Token token : tokenList) {
                        tokens.put(token.id, token);

                        File tokenDir = new File("tokens/" + token.id);
                        if (!tokenDir.exists()) {
                            tokenDir.mkdirs();
                        }
                    }
                    log("已加载 " + tokenList.size() + " 个标识码");
                }
            } else {
                log("标识码文件不存在，将创建新文件");

                Token publicToken = new Token("public", "公共参数", "普通用户使用的公共参数");
                tokens.put(publicToken.id, publicToken);
                saveTokens();
            }
        } catch (Exception e) {
            log("加载标识码失败: " + e.getMessage());
        }
    }

    private void saveTokens() {
        try (FileWriter writer = new FileWriter(TOKENS_FILE)) {
            List<Token> tokenList = new ArrayList<>(tokens.values());
            gson.toJson(tokenList, writer);
            log("标识码数据已保存 (" + tokenList.size() + " 个标识码)");
        } catch (Exception e) {
            log("保存标识码失败: " + e.getMessage());
        }
    }

    void loadActivationCodes() {
        try {
            if (Files.exists(Paths.get(ACTIVATION_CODES_FILE))) {
                String json = new String(Files.readAllBytes(Paths.get(ACTIVATION_CODES_FILE)));
                List<ActivationCode> codeList = gson.fromJson(json, new TypeToken<List<ActivationCode>>(){}.getType());
                if (codeList != null) {
                    for (ActivationCode code : codeList) {
                        activationCodes.put(code.code, code);
                    }
                    log("已加载 " + codeList.size() + " 个激活码");
                }
            } else {
                log("激活码文件不存在，将创建新文件");
            }
        } catch (Exception e) {
            log("加载激活码失败: " + e.getMessage());
        }
    }

    private void saveActivationCodes() {
        try (FileWriter writer = new FileWriter(ACTIVATION_CODES_FILE)) {
            List<ActivationCode> codeList = new ArrayList<>(activationCodes.values());
            gson.toJson(codeList, writer);
            log("激活码已保存 (" + codeList.size() + " 个激活码)");
        } catch (Exception e) {
            log("保存激活码失败: " + e.getMessage());
        }
    }

    void loadBannedIps() {
        try {
            if (Files.exists(Paths.get(BANNED_IPS_FILE))) {
                String json = new String(Files.readAllBytes(Paths.get(BANNED_IPS_FILE)));
                List<String> ips = gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
                if (ips != null) {
                    bannedIps.addAll(ips);
                    log("已加载 " + ips.size() + " 个封禁IP");
                }
            } else {
                log("封禁IP文件不存在，将创建新文件");
            }
        } catch (Exception e) {
            log("加载封禁IP失败: " + e.getMessage());
        }
    }

    private void saveBannedIps() {
        try (FileWriter writer = new FileWriter(BANNED_IPS_FILE)) {
            List<String> ips = new ArrayList<>(bannedIps);
            gson.toJson(ips, writer);
            log("封禁IP数据已保存 (" + ips.size() + " 个IP)");
        } catch (Exception e) {
            log("保存封禁IP失败: " + e.getMessage());
        }
    }

    private void loadLuaScript() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Lua脚本文件", "lua"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (luaScriptManager.loadScript(selectedFile.getAbsolutePath())) {
                updateLuaStatus();
            }
        }
    }

    private void startLuaScript() {
        if (luaScriptManager.startScript()) {
            updateLuaStatus();
        }
    }

    private void stopLuaScript() {
        luaScriptManager.stopScript();
        updateLuaStatus();
    }

    private void reloadLuaScript() {
        if (luaScriptManager.reloadScript()) {
            updateLuaStatus();
        }
    }

    private void updateLuaStatus() {
        luaStatusLabel.setText(luaScriptManager.getStatus());
        boolean isRunning = luaScriptManager.isRunning();
        luaStartButton.setEnabled(!isRunning && luaScriptManager.getCurrentScriptFile() != null);
        luaStopButton.setEnabled(isRunning);
        luaReloadButton.setEnabled(isRunning);
    }
    private JPanel createLuaPanel() {
        JPanel luaPanel = new JPanel(new BorderLayout(10, 10));
        luaPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(new JLabel("当前状态:"));
        luaStatusLabel = new JLabel("未启动");
        statusPanel.add(luaStatusLabel);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        luaLoadButton = new JButton("加载脚本");
        luaStartButton = new JButton("启动");
        luaStopButton = new JButton("停止");
        luaReloadButton = new JButton("重载");

        luaLoadButton.addActionListener(e -> loadLuaScript());
        luaStartButton.addActionListener(e -> startLuaScript());
        luaStopButton.addActionListener(e -> stopLuaScript());
        luaReloadButton.addActionListener(e -> reloadLuaScript());

        controlPanel.add(luaLoadButton);
        controlPanel.add(luaStartButton);
        controlPanel.add(luaStopButton);
        controlPanel.add(luaReloadButton);

        JTextArea infoArea = new JTextArea(10, 50);
        infoArea.setEditable(false);
        infoArea.setText(
                "Lua脚本管理说明:\n\n" +
                        "1. 脚本文件应放在程序运行目录下\n" +
                        "2. 支持的函数:\n" +
                        "   - onStart(): 脚本启动时调用\n" +
                        "   - onStop(): 脚本停止时调用\n" +
                        "   - onReload(): 重载时调用\n" +
                        "   - onSchedule(): 每30秒调用一次\n" +
                        "   - onUserLogin(username, ip): 用户登录时调用\n" +
                        "   - onUserRegister(username, ip): 用户注册时调用\n\n" +
                        "3. 可用API:\n" +
                        "   - server:startServer(), stopServer()\n" +
                        "   - server:reloadUsers(), reloadTokens()\n" +
                        "   - server:banIp(ip)\n" +
                        "   - log(message)\n" +
                        "   - util:sleep(ms), util:getTimestamp()"
        );
        JScrollPane infoScroll = new JScrollPane(infoArea);

        luaPanel.add(statusPanel, BorderLayout.NORTH);
        luaPanel.add(controlPanel, BorderLayout.CENTER);
        luaPanel.add(infoScroll, BorderLayout.SOUTH);

        updateLuaStatus();
        return luaPanel;
    }

    private void createUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel serverPanel = createServerPanel();
        tabbedPane.addTab("服务器控制", serverPanel);

        JPanel tokenPanel = createTokenPanel();
        tabbedPane.addTab("标识码管理", tokenPanel);

        JPanel activationPanel = createActivationPanel();
        tabbedPane.addTab("激活码管理", activationPanel);

        JPanel luaPanel = createLuaPanel();
        tabbedPane.addTab("Lua脚本", luaPanel);

        JPanel backupPanel = createBackupPanel();
        tabbedPane.addTab("数据备份", backupPanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private JPanel createBackupPanel() {
        JPanel backupPanel = new JPanel(new BorderLayout(10, 10));
        backupPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextArea infoArea = new JTextArea(
                "数据备份管理功能说明:\n\n" +
                        "• 创建备份: 将当前所有数据文件打包为ZIP存档\n" +
                        "• 恢复备份: 从备份文件恢复数据（覆盖当前数据）\n" +
                        "• 备份包含: 用户数据、令牌、激活码、封禁IP、数据库文件、配置文件等\n\n" +
                        "注意事项:\n" +
                        "• 恢复操作会覆盖当前所有数据，请谨慎操作\n" +
                        "• 建议在执行重要操作前创建备份\n" +
                        "• 备份文件保存在 backups/ 目录下"
        );
        infoArea.setEditable(false);
        infoArea.setBackground(backupPanel.getBackground());
        infoArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        JScrollPane infoScroll = new JScrollPane(infoArea);
        infoScroll.setPreferredSize(new Dimension(400, 200));
        backupPanel.add(infoScroll, BorderLayout.NORTH);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton createBackupButton = new JButton("创建数据备份");
        JButton manageBackupsButton = new JButton("管理备份文件");
        JButton restoreBackupButton = new JButton("恢复数据备份");

        createBackupButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "确定要创建数据备份吗？", "确认备份",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                boolean success = backupManager.createBackup();
                if (success) {
                    JOptionPane.showMessageDialog(this, "数据备份创建成功！", "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "数据备份创建失败！", "错误",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        manageBackupsButton.addActionListener(e -> {
            backupManager.showBackupDialog();
        });

        restoreBackupButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File("backups"));
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("备份文件", "zip"));

            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                int confirmResult = JOptionPane.showConfirmDialog(this,
                        "确定要从此备份恢复数据吗？这将覆盖当前所有数据！\n文件: " + selectedFile.getName(),
                        "确认恢复", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (confirmResult == JOptionPane.YES_OPTION) {
                    boolean success = backupManager.restoreBackup(selectedFile.getAbsolutePath());
                    if (success) {
                        JOptionPane.showMessageDialog(this, "数据恢复成功！", "成功",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "数据恢复失败！", "错误",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        controlPanel.add(createBackupButton);
        controlPanel.add(manageBackupsButton);
        controlPanel.add(restoreBackupButton);

        backupPanel.add(controlPanel, BorderLayout.CENTER);
        return backupPanel;
    }

    private JPanel createServerPanel() {
        JPanel serverPanel = new JPanel(new BorderLayout());
        serverPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(logArea);
        serverPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        startButton = new JButton("启动服务");
        stopButton = new JButton("停止服务");
        stopButton.setEnabled(false);

        backupButton = new JButton("快速备份");
        backupButton.addActionListener(e -> {
            boolean success = backupManager.createBackup();
            if (success) {
                JOptionPane.showMessageDialog(this, "快速备份创建成功！", "成功",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        startWebButton = new JButton("使用网页管理");
        startWebButton.addActionListener(e -> startWebManager());
        stopWebButton = new JButton("关闭网页管理");
        stopWebButton.addActionListener(e -> stopWebManager());
        stopWebButton.setEnabled(false);
        controlPanel.add(backupButton);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(startWebButton);
        controlPanel.add(stopWebButton);

        serverPanel.add(controlPanel, BorderLayout.SOUTH);

        return serverPanel;
    }

    private JPanel createTokenPanel() {
        JPanel tokenPanel = new JPanel(new BorderLayout(10, 10));
        tokenPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] columnNames = {"标识码ID", "名称", "描述", "创建时间"};
        tokenTableModel = new DefaultTableModel(columnNames, 0);
        JTable tokenTable = new JTable(tokenTableModel);
        JScrollPane tableScrollPane = new JScrollPane(tokenTable);

        updateTokenTable();

        tokenPanel.add(tableScrollPane, BorderLayout.CENTER);

        JPanel addTokenPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        addTokenPanel.setBorder(new TitledBorder("添加新标识码"));

        addTokenPanel.add(new JLabel("标识码名称:"));
        newTokenNameField = new JTextField();
        addTokenPanel.add(newTokenNameField);

        addTokenPanel.add(new JLabel("标识码描述:"));
        newTokenDescField = new JTextField();
        addTokenPanel.add(newTokenDescField);

        JButton addTokenButton = new JButton("添加标识码");
        addTokenButton.addActionListener(e -> addNewToken());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(addTokenButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(addTokenPanel, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        tokenPanel.add(southPanel, BorderLayout.SOUTH);

        return tokenPanel;
    }

    private void addNewToken() {
        String name = newTokenNameField.getText().trim();
        String desc = newTokenDescField.getText().trim();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "标识码名称不能为空", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String id = "token_" + System.currentTimeMillis();

        Token newToken = new Token(id, name, desc);
        tokens.put(id, newToken);

        File tokenDir = new File("tokens/" + id);
        if (!tokenDir.exists()) {
            tokenDir.mkdirs();
        }

        saveTokens();
        updateTokenTable();

        newTokenNameField.setText("");
        newTokenDescField.setText("");

        log("已创建新标识码: " + name + " (" + id + ")");
    }

    private void updateTokenTable() {
        tokenTableModel.setRowCount(0);
        for (Token token : tokens.values()) {
            tokenTableModel.addRow(new Object[]{
                    token.id,
                    token.name,
                    token.description,
                    new Date(token.createdAt)
            });
        }
    }

    private JPanel createActivationPanel() {
        JPanel activationPanel = new JPanel(new BorderLayout(10, 10));
        activationPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] columnNames = {"激活码", "关联标识码", "状态", "创建时间", "使用时间", "使用用户"};
        activationTableModel = new DefaultTableModel(columnNames, 0);
        JTable activationTable = new JTable(activationTableModel);
        JScrollPane tableScrollPane = new JScrollPane(activationTable);

        updateActivationTable();

        activationPanel.add(tableScrollPane, BorderLayout.CENTER);

        JPanel generatePanel = new JPanel(new GridLayout(3, 2, 5, 5));
        generatePanel.setBorder(new TitledBorder("生成激活码"));

        generatePanel.add(new JLabel("关联标识码:"));
        tokenComboBox = new JComboBox<>();
        updateTokenComboBox();
        generatePanel.add(tokenComboBox);

        generatePanel.add(new JLabel("生成数量:"));
        activationCountField = new JTextField("10");
        generatePanel.add(activationCountField);

        JButton generateButton = new JButton("生成激活码");
        generateButton.addActionListener(e -> generateActivationCodes());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(generateButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(generatePanel, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        activationPanel.add(southPanel, BorderLayout.SOUTH);

        return activationPanel;
    }

    private void updateTokenComboBox() {
        tokenComboBox.removeAllItems();
        for (Token token : tokens.values()) {
            tokenComboBox.addItem(token.name + " (" + token.id + ")");
        }
    }

    private void generateActivationCodes() {
        int selectedIndex = tokenComboBox.getSelectedIndex();
        if (selectedIndex == -1) {
            JOptionPane.showMessageDialog(this, "请先创建标识码", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selectedItem = (String) tokenComboBox.getSelectedItem();
        String tokenId = selectedItem.substring(selectedItem.indexOf("(") + 1, selectedItem.indexOf(")"));

        int count;
        try {
            count = Integer.parseInt(activationCountField.getText().trim());
            if (count <= 0 || count > 1000) {
                JOptionPane.showMessageDialog(this, "生成数量必须在1-1000之间", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<ActivationCode> newCodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code = generateActivationCode();
            ActivationCode activationCode = new ActivationCode(code, tokenId);
            activationCodes.put(code, activationCode);
            newCodes.add(activationCode);
        }

        saveActivationCodes();
        updateActivationTable();

        saveActivationCodesToFile(newCodes, tokenId);

        log("已生成 " + count + " 个激活码，关联到标识码: " + tokenId);
    }

    private String generateActivationCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                code.append("-");
            }
            code.append(characters.charAt(ThreadLocalRandom.current().nextInt(characters.length())));
        }
        return code.toString();
    }

    private void saveActivationCodesToFile(List<ActivationCode> codes, String tokenId) {
        try {
            File file = new File("activation_codes_" + tokenId + "_" + System.currentTimeMillis() + ".txt");
            try (FileWriter writer = new FileWriter(file)) {
                for (ActivationCode code : codes) {
                    writer.write(code.code + "\n");
                }
            }
            log("激活码已保存到文件: " + file.getName());
        } catch (IOException e) {
            log("保存激活码到文件失败: " + e.getMessage());
        }
    }

    private void updateActivationTable() {
        activationTableModel.setRowCount(0);
        for (ActivationCode code : activationCodes.values()) {
            activationTableModel.addRow(new Object[]{
                    code.code,
                    code.tokenId,
                    code.used ? "已使用" : "未使用",
                    new Date(code.createdAt),
                    code.usedAt > 0 ? new Date(code.usedAt) : "",
                    code.usedBy != null ? code.usedBy : ""
            });
        }
    }

    public void startServer() {
        if (isRunning) return;

        log("正在启动服务端...");
        isRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);

        try {
            startKcpServer();
            startTcpServer();

            log("服务端已成功启动");
        } catch (Exception e) {
            log("服务端启动异常: " + e.getMessage());
            stopServer();
        }
    }

    private void handleTcpClient(java.net.Socket clientSocket) {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        String clientInfo = clientIp + ":" + clientSocket.getPort();

        if (bannedIps.contains(clientIp)) {
            int attempts = bannedIpAttempts.getOrDefault(clientIp, 0) + 1;
            bannedIpAttempts.put(clientIp, attempts);
            log("封禁IP尝试连接: " + clientIp + " (次数: " + attempts + ")");
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
            return;
        }

        log("新的TCP客户端连接: " + clientInfo);

        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            String authData = dis.readUTF();

            if (authData.startsWith("VERIFY:")) {
                handleVerifyRequest(authData, dos, clientIp);
            } else {
                String[] authParts = authData.split(":", 2);
                if (authParts.length != 2) {
                    dos.writeUTF("AUTH_FAIL:无效的认证格式");
                    return;
                }

                String token = authParts[0];
                String hwid = authParts[1];
                log("收到认证请求: HWID=" + hwid + " (来自IP: " + clientIp + ")");

                handleTcpAuth(token, hwid, dos, clientIp);
            }
        } catch (IOException e) {
            log("TCP客户端处理异常: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                log("TCP客户端断开: " + clientInfo);
            } catch (IOException e) {
                
            }
        }
    }

    private void startKcpServer() {
        group = new NioEventLoopGroup();
        UkcpServerBootstrap bootstrap = new UkcpServerBootstrap();
        bootstrap.group(group)
                .channel(UkcpServerChannel.class)
                .childOption(ChannelOption.valueOf("conv"), 123456) 
                .childOption(ChannelOption.SO_RCVBUF, 1024 * 1024)
                .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                
                .childOption(ChannelOption.valueOf("nodelay"), 1)
                .childOption(ChannelOption.valueOf("interval"), 10)
                .childOption(ChannelOption.valueOf("resend"), 2)
                .childOption(ChannelOption.valueOf("nc"), 1)
                .childHandler(new ChannelInitializer<UkcpChannel>() {
                    @Override
                    protected void initChannel(UkcpChannel ch) {
                        ch.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
                        ch.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));
                        ch.pipeline().addLast(new KcpServerHandler());
                    }
                });

        bootstrap.bind(KCP_PORT).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log("KCP服务端启动成功，监听端口: " + KCP_PORT);
            } else {
                log("KCP服务端启动失败: " + future.cause().getMessage());
                stopServer();
            }
        });
    }

    private void startTcpServer() {
        tcpRunning = true;
        tcpServerThread = new Thread(() -> {
            try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(TCP_PORT)) {
                log("TCP服务端启动成功，监听端口: " + TCP_PORT);
                while (tcpRunning) {
                    java.net.Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleTcpClient(clientSocket)).start();
                }
            } catch (IOException e) {
                if (tcpRunning) {
                    log("TCP服务器异常: " + e.getMessage());
                }
            }
        });
        tcpServerThread.setName("TCP-Server-Thread");
        tcpServerThread.start();
    }

    private void handleVerifyRequest(String authData, DataOutputStream dos, String clientIp) throws IOException {
        String[] parts = authData.split(":", 3);
        if (parts.length != 3) {
            dos.writeUTF("VERIFY_FAIL:无效的验证格式，正确格式: VERIFY:HWID:TOKEN_ID");
            return;
        }

        String hwid = parts[1];
        String tokenId = parts[2];

        log("收到验证请求: HWID=" + hwid + ", TokenID=" + tokenId + " (来自IP: " + clientIp + ")");

        User user = findUserByHwid(hwid);
        if (user == null) {
            dos.writeUTF("VERIFY_FAIL:未找到与该HWID关联的用户");
            log("验证失败: 未找到与HWID关联的用户 - " + hwid);
            return;
        }

        if (!user.tokenCode.equals(tokenId)) {
            dos.writeUTF("VERIFY_FAIL:标识码不匹配");
            log("验证失败: 标识码不匹配 - 用户: " + user.username + ", 期望: " + user.tokenCode + ", 实际: " + tokenId);
            return;
        }

        dos.writeUTF("VERIFY_SUCCESS:验证成功");
        log("验证成功: 用户=" + user.username + ", HWID=" + hwid + ", TokenID=" + tokenId);
    }

    private void handleTcpAuth(String token, String hwid, DataOutputStream dos, String clientIp) throws IOException {
        if (!"gikrhig4343hier433uh3gie473343rjioug52ujeuiorgr".equals(token)) {
            dos.writeUTF("AUTH_FAIL:无效令牌");
            return;
        }

        User user = findUserByHwid(hwid);
        if (user == null) {
            dos.writeUTF("AUTH_FAIL:HWID未授权");
            log("HWID未授权: " + hwid);
            return;
        }

        if (isSameIp(user.registerIp, clientIp)) {
            log("注册IP和验证IP相同，跳过HWID验证: " + clientIp);
        } else {
            if (!hwid.equals(user.hwid)) {
                dos.writeUTF("AUTH_FAIL:HWID不匹配");
                log("HWID不匹配: " + hwid + " != " + user.hwid);
                return;
            }
        }

        String config = getParamConfigForUser(user);
        if (config == null || config.isEmpty()) {
            dos.writeUTF("AUTH_FAIL:参数配置不存在");
            log("参数配置不存在: " + user.currentParam);
            return;
        }

        String encryptionKey = generateAESKey();
        String encryptedConfig = encryptAES(config, encryptionKey);

        if (encryptedConfig == null) {
            dos.writeUTF("AUTH_FAIL:参数加密失败");
            log("参数加密失败: " + user.currentParam);
            return;
        }

        dos.writeUTF("AUTH_SUCCESS");
        dos.writeUTF(encryptionKey);

        byte[] configData = encryptedConfig.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(configData.length);
        dos.write(configData);
        dos.flush();
        log("认证成功: HWID=" + hwid + " 用户=" + user.username + " 参数=" + user.currentParam + " (已加密传输)");
    }

    private boolean isSameIp(String ip1, String ip2) {
        if (ip1 == null || ip2 == null) {
            return false;
        }

        String cleanIp1 = removePort(ip1);
        String cleanIp2 = removePort(ip2);

        return cleanIp1.equals(cleanIp2);
    }

    private String removePort(String ipWithPort) {
        if (ipWithPort == null) {
            return null;
        }

        int lastColon = ipWithPort.lastIndexOf(':');
        if (lastColon == -1) {
            return ipWithPort;
        }

        String afterColon = ipWithPort.substring(lastColon + 1);
        try {
            Integer.parseInt(afterColon);
            return ipWithPort.substring(0, lastColon);
        } catch (NumberFormatException e) {
            return ipWithPort;
        }
    }

    private User findUserByHwid(String hwid) {
        for (User user : users.values()) {
            if (hwid.equals(user.hwid)) {
                return user;
            }
        }
        return null;
    }

    private String getParamConfigForUser(User user) {
        try {
            String paramFile;
            if (user.getTokenCode() != null && !user.getTokenCode().equals("public")) {
                paramFile = "tokens/" + user.getTokenCode() + "/param.cfg";
                log("使用标识码参数文件: " + paramFile + " for user: " + user.getUsername());
            } else {
                paramFile = user.getCurrentParam() + ".cfg";
                log("使用公共参数文件: " + paramFile + " for user: " + user.getUsername());
            }

            Path configPath = Paths.get(paramFile);
            if (Files.exists(configPath)) {
                return new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
            } else {
                log("参数文件不存在: " + paramFile);
                return "";
            }
        } catch (IOException e) {
            log("读取参数文件失败: " + e.getMessage());
            return "";
        }
    }

    public void stopServer() {
        if (!isRunning) return;
        log("正在停止服务端...");
        isRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        tcpRunning = false;
        if (tcpServerThread != null) {
            try {
                new java.net.Socket("localhost", TCP_PORT).close();
            } catch (IOException e) {
            }
            tcpServerThread.interrupt();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        log("服务端已停止");
    }

    public void log(String message) {

        if (message.contains("State=-1 after update()") ||
                message.contains("State=-1") ||
                message.contains("after update()")) {
            return;
        }

        String level = "INFO";
        String logMessage = message;

        if (message.contains("[ERROR]")) {
            level = "ERROR";
        } else if (message.contains("[WARNING]")) {
            level = "WARNING";
        } else if (message.contains("[INFO]")) {
            level = "INFO";
        }

        String time = "[" + new Date() + "]";

        LogEntry entry = new LogEntry(time, level, logMessage);
        synchronized(logs) {
            logs.add(entry);

            if (logs.size() > 1000) {
                logs.remove(0);
            }
        }

        SwingUtilities.invokeLater(() -> {
            logArea.append(time + " " + logMessage + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public List<LogEntry> getLogs() {
        synchronized(logs) {
            return new ArrayList<>(logs);
        }
    }

    void startWebManager() {
        if (!isWebManagerRunning) {
            try {
                webManager.startWebServer();
                isWebManagerRunning = true;
                startWebButton.setEnabled(false);
                stopWebButton.setEnabled(true);

                log("网页管理已启动");

                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        startBuiltInBrowser();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log("启动内置浏览器线程被中断");
                    }
                }).start();
            } catch (Exception e) {
                log("网页管理启动失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    void stopWebManager() {
        if (isWebManagerRunning) {
            try {
                webManager.stopWebServer();
                isWebManagerRunning = false;
                startWebButton.setEnabled(true);
                stopWebButton.setEnabled(false);
                log("Web管理器已停止");
            } catch (Exception e) {
                log("Web管理器停止失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    
    private void startBuiltInBrowser() {
        try {
            if (browserManager == null) {
                browserManager = new BuiltInBrowserManager();
            }

            
            if (browserManager.isBrowserAvailable()) {
                
                String webUrl;
                if (webManager.isUseHttps() && new File(webManager.getKeystorePath()).exists()) {
                    
                    webUrl = "https://localhost:" + webManager.getHttpsPort() + "/server";
                } else {
                    
                    webUrl = "http://localhost:" + webManager.getWebPort() + "/server";
                }

                log("请在浏览器访问: " + webUrl);

                
                boolean started = browserManager.startBrowser(webUrl);
                if (started) {
                    log("浏览器已启动");
                } else {
                    log("[WARNING] 内置浏览器启动失败");
                    
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(webUrl));
                        log("已尝试使用系统默认浏览器打开控制台");
                    } catch (Exception ex) {
                        log("打开浏览器失败: " + ex.getMessage());
                    }
                }
            } else {
                log("[INFO] 内置浏览器组件不可用，尝试使用系统默认浏览器");
                
                try {
                    String webUrl = webManager.isUseHttps() ?
                            "https://localhost:" + webManager.getHttpsPort() + "/server" :
                            "http://localhost:" + webManager.getWebPort() + "/server";
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(webUrl));
                    log("已使用系统默认浏览器打开控制台: " + webUrl);
                } catch (Exception ex) {
                    log("[ERROR] 打开系统浏览器失败: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            log("[ERROR] 启动内置浏览器时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    void loadUsers() {
        try {
            List<User> userList = databaseManager.getAllUsers();
            users.clear(); 

            for (User user : userList) {
                
                users.put(user.getUsername(), user);

                if (user.getHwid() != null && !user.getHwid().isEmpty()) {
                    String trimmedHwid = user.getHwid().trim();
                    hwidToParamMap.put(trimmedHwid, user.getCurrentParam());
                    log("加载用户映射: " + user.getUsername() + " -> " + trimmedHwid + " -> " + user.getCurrentParam());
                }
            }
            log("已从数据库加载 " + userList.size() + " 个用户到内存");
        } catch (Exception e) {
            log("加载用户数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveUsers() {
        try (FileWriter writer = new FileWriter(USERS_FILE)) {
            List<User> userList = new ArrayList<>(users.values());
            gson.toJson(userList, writer);
            log("用户数据已保存 (" + userList.size() + " 个用户)");
        } catch (Exception e) {
            log("保存用户数据失败: " + e.getMessage());
        }
    }

    private void handleLogin(long requestId, String username, String password, Channel channel, String ip) {
        boolean loginSuccess = databaseManager.verifyPassword(username, password);
        User user = databaseManager.getUserByUsername(username);
        String response;
        if (loginSuccess && user != null) {
            response = "LOGIN_SUCCESS:" + user.getCurrentParam();
            log("用户登录成功: " + username + " (参数: " + user.getCurrentParam() + ")");
            luaScriptManager.onUserLogin(username, ip);
        } else {
            String reason = user == null ? "用户不存在" : "密码错误";
            response = "LOGIN_FAIL:" + reason;
            log("用户登录失败: " + username + " - " + reason);
        }
        sendEncryptedResponse(requestId, response, channel, ip);
    }

    private void handleRegister(long requestId, String username, String email, String password,
                                String activationCode, Channel channel, String ip) {
        String response;

        if (bannedIps.contains(ip)) {
            int attempts = bannedIpAttempts.getOrDefault(ip, 0) + 1;
            bannedIpAttempts.put(ip, attempts);
            response = "REGISTER_FAIL:IP被封禁";
            log("封禁IP尝试注册: " + ip + " (次数: " + attempts + ")");
            sendEncryptedResponse(requestId, response, channel, ip);
            return;
        }

        int registerCount = ipRegisterCount.getOrDefault(ip, 0);
        if (registerCount >= 3) {
            recordRegisterAttempt(ip);
            response = "REGISTER_FAIL:该IP注册账号已达上限";
            log("IP注册已达上限: " + ip + " (已有 " + registerCount + " 个账号)");
            sendEncryptedResponse(requestId, response, channel, ip);
            return;
        }

        if (users.containsKey(username)) {
            response = "REGISTER_FAIL:用户名已存在";
            log("注册失败: " + username + " - 用户名已存在");
        } else {
            if (checkRegisterFrequency(ip)) {
                banIp(ip);
                response = "REGISTER_FAIL:注册过于频繁，IP已被封禁";
                log("因频繁注册被封禁: " + ip);
            } else {
                User newUser = new User(username, email, password);
                newUser.setRegisterIp(ip);

                if (activationCode != null && !activationCode.isEmpty()) {
                    ActivationCode code = activationCodes.get(activationCode);
                    if (code != null && !code.used) {
                        newUser.setTokenCode(code.tokenId);
                        code.used = true;
                        code.usedAt = System.currentTimeMillis();
                        code.usedBy = username;
                        saveActivationCodes();
                        log("用户 " + username + " 使用激活码 " + activationCode + " 关联到标识码 " + code.tokenId);
                    } else {
                        response = "REGISTER_FAIL:无效的激活码";
                        sendEncryptedResponse(requestId, response, channel, ip);
                        log("注册失败: " + username + " - 无效的激活码");
                        return;
                    }
                } else {
                    newUser.setTokenCode("public");
                }
                if (databaseManager.addUser(newUser)) {
                    users.put(username, newUser);

                    log("新用户注册成功: " + username + " (已同步到数据库和内存)");
                    luaScriptManager.onUserRegister(username, ip);
                } else {
                    response = "REGISTER_FAIL:用户创建失败";
                    log("用户注册失败: " + username + " - 数据库创建失败");
                }
                users.put(username, newUser);
                saveUsers();
                ipRegisterCount.put(ip, registerCount + 1);
                response = "REGISTER_SUCCESS";
                log("新用户注册成功: " + username + " (来自IP: " + ip +
                        (newUser.tokenCode.equals("public") ? ", 公共用户" : ", 标识码: " + newUser.tokenCode) + ")");
            }
        }
        sendEncryptedResponse(requestId, response, channel, ip);
    }

    private void recordRegisterAttempt(String ip) {
        long now = System.currentTimeMillis();
        List<Long> timestamps = ipRegisterTimestamps.computeIfAbsent(ip, k -> new ArrayList<>());
        timestamps.add(now);
    }

    private boolean checkRegisterFrequency(String ip) {
        long now = System.currentTimeMillis();
        List<Long> timestamps = ipRegisterTimestamps.computeIfAbsent(ip, k -> new ArrayList<>());
        timestamps.removeIf(timestamp -> now - timestamp > 60000);
        timestamps.add(now);
        return timestamps.size() >= 5;
    }

    void banIp(String ip) {
        bannedIps.add(ip);
        saveBannedIps();
        log("已封禁IP: " + ip);
    }

    private void sendEncryptedResponse(long requestId, String response, Channel channel, String ip) {
        try {
            PublicKey clientPublicKey = clientPublicKeys.get(channel);
            if (clientPublicKey != null) {
                String encryptedResponse = encryptRSA(response, clientPublicKey);
                String finalResponse = requestId + ":" + encryptedResponse;
                channel.writeAndFlush(finalResponse + "\n");
                log("发送加密响应给 " + ip + ": " + response);
            } else {
                channel.writeAndFlush(requestId + ":" + response + "\n");
                log("发送未加密响应给 " + ip + " (无公钥): " + response);
            }
        } catch (Exception e) {
            log("响应加密失败: " + e.getMessage());
            channel.writeAndFlush(requestId + ":" + response + "\n");
        }
    }

    private class KcpServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof String) {
                String message = ((String) msg).trim();

                String ip = "unknown";
                if (ctx.channel().remoteAddress() instanceof java.net.InetSocketAddress) {
                    java.net.InetSocketAddress addr = (java.net.InetSocketAddress) ctx.channel().remoteAddress();
                    ip = addr.getAddress().getHostAddress();
                }

                if (bannedIps.contains(ip)) {
                    int attempts = bannedIpAttempts.getOrDefault(ip, 0) + 1;
                    bannedIpAttempts.put(ip, attempts);
                    log("封禁IP尝试请求: " + ip + " (次数: " + attempts + ")");
                    ctx.close();
                    return;
                }

                log("KCP请求: " + (message.length() > 100 ? message.substring(0, 100) + "..." : message) + " (来自IP: " + ip + ")");

                if (message.startsWith("KEY_EXCHANGE:")) {
                    try {
                        String clientPublicKeyStr = message.substring("KEY_EXCHANGE:".length());
                        PublicKey clientPublicKey = stringToPublicKey(clientPublicKeyStr);
                        clientPublicKeys.put(ctx.channel(), clientPublicKey);

                        String serverPublicKeyStr = publicKeyToString(serverRsaKeyPair.getPublic());
                        String keyExchangeResponse = "KEY_EXCHANGE:" + serverPublicKeyStr;
                        ctx.channel().writeAndFlush(keyExchangeResponse);
                        log("与客户端完成密钥交换: " + ip);
                    } catch (Exception e) {
                        log("处理客户端公钥失败: " + e.getMessage());
                    }
                    return;
                }

                int firstColon = message.indexOf(':');
                if (firstColon > 0) {
                    try {
                        long requestId = Long.parseLong(message.substring(0, firstColon));
                        String encryptedContent = message.substring(firstColon + 1);

                        String decryptedContent = decryptRSA(encryptedContent, serverRsaKeyPair.getPrivate());

                        log("解密后消息: " + decryptedContent + " (来自IP: " + ip + ")");

                        if (decryptedContent.startsWith("LOGIN:")) {
                            String[] parts = decryptedContent.split(":", 3);
                            if (parts.length == 3) {
                                handleLogin(requestId, parts[1], parts[2], ctx.channel(), ip);
                            }
                        } else if (decryptedContent.startsWith("REGISTER:")) {
                            String[] parts = decryptedContent.split(":", 5);
                            if (parts.length >= 4) {
                                String activationCode = parts.length == 5 ? parts[4] : "";
                                handleRegister(requestId, parts[1], parts[2], parts[3],
                                        activationCode, ctx.channel(), ip);
                            }
                        } else if (decryptedContent.startsWith("UPDATE_HWID:")) {
                            String[] parts = decryptedContent.split(":", 4);
                            if (parts.length == 4) {
                                handleUpdateHWID(requestId, parts[1], parts[2], parts[3], ctx.channel(), ip);
                            }
                        } else if (decryptedContent.equals("GET_PARAM_CONFIGS")) {
                            handleGetParamConfigs(requestId, ctx.channel(), ip);
                        } else if (decryptedContent.startsWith("SWITCH_PARAM:")) {
                            String[] parts = decryptedContent.split(":", 4);
                            if (parts.length == 4) {
                                handleSwitchParam(requestId, parts[1], parts[2], parts[3], ctx.channel(), ip);
                            }
                        } else if (decryptedContent.startsWith("GET_TOKEN_CODE:")) {
                            String[] parts = decryptedContent.split(":", 3);
                            if (parts.length == 3) {
                                handleGetTokenCode(requestId, parts[1], parts[2], ctx.channel(), ip);
                            }
                        } else if (decryptedContent.startsWith("GET_USER_ROLE:")) {
                            String[] parts = decryptedContent.split(":", 3);
                            if (parts.length == 3) {
                                handleGetUserRole(requestId, parts[1], parts[2], ctx.channel(), ip);
                            }
                        }
                    } catch (NumberFormatException e) {
                        log("无效的请求ID格式: " + message);
                    } catch (Exception e) {
                        log("消息解密失败: " + e.getMessage());
                        processUnencryptedMessage(message, ctx, ip);
                    }
                } else {
                    processUnencryptedMessage(message, ctx, ip);
                }
            }
        }

        private void processUnencryptedMessage(String message, ChannelHandlerContext ctx, String ip) {
            int firstColon = message.indexOf(':');
            if (firstColon > 0) {
                try {
                    long requestId = Long.parseLong(message.substring(0, firstColon));
                    String content = message.substring(firstColon + 1);

                    if (content.startsWith("LOGIN:")) {
                        String[] parts = content.split(":", 3);
                        if (parts.length == 3) {
                            handleLogin(requestId, parts[1], parts[2], ctx.channel(), ip);
                        }
                    } else if (content.startsWith("REGISTER:")) {
                        String[] parts = content.split(":", 5);
                        if (parts.length >= 4) {
                            String activationCode = parts.length == 5 ? parts[4] : "";
                            handleRegister(requestId, parts[1], parts[2], parts[3],
                                    activationCode, ctx.channel(), ip);
                        }
                    } else if (content.startsWith("UPDATE_HWID:")) {
                        String[] parts = content.split(":", 4);
                        if (parts.length == 4) {
                            handleUpdateHWID(requestId, parts[1], parts[2], parts[3], ctx.channel(), ip);
                        }
                    } else if (content.equals("GET_PARAM_CONFIGS")) {
                        handleGetParamConfigs(requestId, ctx.channel(), ip);
                    } else if (content.startsWith("SWITCH_PARAM:")) {
                        String[] parts = content.split(":", 4);
                        if (parts.length == 4) {
                            handleSwitchParam(requestId, parts[1], parts[2], parts[3], ctx.channel(), ip);
                        }
                    } else if (content.startsWith("GET_TOKEN_CODE:")) {
                        String[] parts = content.split(":", 3);
                        if (parts.length == 3) {
                            handleGetTokenCode(requestId, parts[1], parts[2], ctx.channel(), ip);
                        }
                    } else if (content.startsWith("GET_USER_ROLE:")) {
                        String[] parts = content.split(":", 3);
                        if (parts.length == 3) {
                            handleGetUserRole(requestId, parts[1], parts[2], ctx.channel(), ip);
                        }
                    }
                } catch (NumberFormatException e) {
                    log("无效的请求ID格式: " + message);
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            
            clientPublicKeys.remove(ctx.channel());
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log("KCP处理异常: " + cause.getMessage());
            ctx.close();
        }
    }

    private void handleUpdateHWID(long requestId, String username, String password, String hwid, Channel channel, String ip) {
        boolean authSuccess = databaseManager.verifyPassword(username, password);
        User user = databaseManager.getUserByUsername(username);
        String response;

        if (authSuccess && user != null) {
            if (user.getHwid() != null && !user.getHwid().isEmpty()) {
                String oldTrimmedHwid = user.getHwid().trim();
                hwidToParamMap.remove(oldTrimmedHwid);
            }

            user.setHwid(hwid);
            String newTrimmedHwid = hwid.trim();
            hwidToParamMap.put(newTrimmedHwid, user.getCurrentParam());

            if (databaseManager.updateUserHwid(username, hwid)) {
                response = "HWID_UPDATE_SUCCESS";
                log("HWID更新成功: " + username + " -> " + newTrimmedHwid);
            } else {
                response = "HWID_UPDATE_FAIL:数据库更新失败";
                log("HWID更新失败: " + username + " - 数据库更新失败");
            }
        } else {
            String reason = user == null ? "用户不存在" : "密码错误";
            response = "HWID_UPDATE_FAIL:" + reason;
            log("HWID更新失败: " + username + " - " + reason);
        }
        sendEncryptedResponse(requestId, response, channel, ip);
    }

    private void handleGetParamConfigs(long requestId, Channel channel, String ip) {
        StringBuilder configsBuilder = new StringBuilder("[");
        boolean first = true;
        for (Token token : tokens.values()) {
            if (!first) {
                configsBuilder.append(",");
            }
            configsBuilder.append("{\"id\":\"").append(token.id)
                    .append("\",\"name\":\"").append(token.name)
                    .append("\",\"description\":\"").append(token.description)
                    .append("\",\"isActive\":").append(token.id.equals("public"))
                    .append("}");
            first = false;
        }
        configsBuilder.append("]");

        String response = "PARAM_CONFIGS:" + configsBuilder.toString();
        sendEncryptedResponse(requestId, response, channel, ip);
        log("发送参数配置列表给 " + ip);
    }

    private void handleSwitchParam(long requestId, String username, String password, String paramId, Channel channel, String ip) {
        boolean authSuccess = databaseManager.verifyPassword(username, password);
        User user = databaseManager.getUserByUsername(username);
        String response;
        if (authSuccess && user != null) {
            if (isValidParamId(paramId)) {
                user.setCurrentParam(paramId);

                if (user.getHwid() != null && !user.getHwid().isEmpty()) {
                    String trimmedHwid = user.getHwid().trim();
                    hwidToParamMap.put(trimmedHwid, paramId);
                }

                if (databaseManager.updateUser(user)) {
                    response = "SWITCH_PARAM_SUCCESS:" + paramId;
                    log("参数切换成功: " + username + " -> " + paramId);
                } else {
                    response = "SWITCH_PARAM_FAIL:数据库更新失败";
                    log("参数切换失败: " + username + " - 数据库更新失败");
                }
            } else {
                response = "SWITCH_PARAM_FAIL:无效的参数ID";
                log("参数切换失败: " + username + " - 无效的参数ID");
            }
        } else {
            String reason = user == null ? "用户不存在" : "密码错误";
            response = "SWITCH_PARAM_FAIL:" + reason;
            log("参数切换失败: " + username + " - " + reason);
        }
        sendEncryptedResponse(requestId, response, channel, ip);
    }


    private void handleGetTokenCode(long requestId, String username, String password, Channel channel, String ip) {
        log("收到身份申请请求 - 请求ID: " + requestId + ", 用户名: " + username + ", IP: " + ip);

        boolean authSuccess = databaseManager.verifyPassword(username, password);
        User user = databaseManager.getUserByUsername(username);
        String response;
        if (authSuccess && user != null) {
            response = "TOKEN_CODE_SUCCESS:" + user.getTokenCode();
            log("身份验证成功 - 请求ID: " + requestId + ", 用户名: " + username + ", 返回tokenCode: " + user.getTokenCode());
        } else {
            String reason = user == null ? "用户不存在" : "密码错误";
            response = "TOKEN_CODE_FAIL:" + reason;
            log("身份验证失败 - 请求ID: " + requestId + ", 用户名: " + username + ", 原因: " + reason);
        }

        log("发送身份申请响应 - 请求ID: " + requestId + ", 响应内容: " + response);
        sendEncryptedResponse(requestId, response, channel, ip);
    }

    private void handleGetUserRole(long requestId, String username, String password, Channel channel, String ip) {
        log("收到用户角色请求 - 请求ID: " + requestId + ", 用户名: " + username + ", IP: " + ip);

        boolean authSuccess = databaseManager.verifyPassword(username, password);
        User user = databaseManager.getUserByUsername(username);
        String response;
        if (authSuccess && user != null) {
            response = "USER_ROLE_SUCCESS:" + user.getRole();
            log("用户角色查询成功 - 请求ID: " + requestId + ", 用户名: " + username + ", 角色: " + user.getRole());
        } else {
            String reason = user == null ? "用户不存在" : "密码错误";
            response = "USER_ROLE_FAIL:" + reason;
            log("用户角色查询失败 - 请求ID: " + requestId + ", 用户名: " + username + ", 原因: " + reason);
        }

        sendEncryptedResponse(requestId, response, channel, ip);
    }

    private boolean isValidParamId(String paramId) {
        return tokens.containsKey(paramId) || paramId.equals("1_mode") ||
                paramId.equals("2_mode") || paramId.equals("3_mode");
    }

    private static class Token {
        String id;
        String name;
        String description;
        long createdAt;

        public Token(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static class ActivationCode {
        String code;
        String tokenId;
        boolean used = false;
        long createdAt;
        long usedAt = 0;
        String usedBy;

        public ActivationCode(String code, String tokenId) {
            this.code = code;
            this.tokenId = tokenId;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("设置系统LookAndFeel失败: " + e.getMessage());
            }

            String encryptionKey = showEncryptionKeyDialog();
            if (encryptionKey == null) {
                System.exit(0);
                return;
            }

            DataEncryptionUtil.setEncryptionKey(encryptionKey);

            ServerUI server = ServerUI.getInstance();
            server.setVisible(true);
        });
    }

    private static String showEncryptionKeyDialog() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel infoLabel = new JLabel("<html><b>请输入数据库加密密钥</b><br>" +
                "密钥长度建议为16字符<br>" +
                "请妥善保管此密钥<br>" +
                "！！！忘记密钥将无法访问数据！！！</html>");
        panel.add(infoLabel, BorderLayout.NORTH);

        JPasswordField passwordField = new JPasswordField(20);
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        inputPanel.add(new JLabel("密钥:"));
        inputPanel.add(passwordField);
        panel.add(inputPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        JDialog dialog = new JDialog((Frame) null, "输入加密密钥", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.getContentPane().add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        final String[] result = new String[1];

        okButton.addActionListener(e -> {
            char[] passwordChars = passwordField.getPassword();
            if (passwordChars.length == 0) {
                JOptionPane.showMessageDialog(dialog, "密钥不能为空", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            result[0] = new String(passwordChars);
            Arrays.fill(passwordChars, ' '); 
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> {
            result[0] = null;
            dialog.dispose();
        });

        passwordField.addActionListener(e -> okButton.doClick());

        dialog.setVisible(true);
        return result[0];
    }
}