package dev;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DataBackupManager {
    private static DataBackupManager instance;
    private final ServerUI serverUI;

    
    private static final String[] BACKUP_PATHS = {
            "users.json",
            "tokens.json",
            "activation_codes.json",
            "banned_ips.json",
            "database/",
            "tokens/",
            "ssl/",
            "1_mode.cfg",
            "2_mode.cfg",
            "3_mode.cfg"
    };

    
    private static final String BACKUP_DIR = "backups";

    private DataBackupManager(ServerUI serverUI) {
        this.serverUI = serverUI;
        createBackupDirectory();
    }

    public static DataBackupManager getInstance(ServerUI serverUI) {
        if (instance == null) {
            synchronized (DataBackupManager.class) {
                if (instance == null) {
                    instance = new DataBackupManager(serverUI);
                }
            }
        }
        return instance;
    }

    private void createBackupDirectory() {
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
    }

    /**
     * 创建数据备份
     */
    public boolean createBackup() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String backupFileName = BACKUP_DIR + File.separator + "backup_" + timestamp + ".zip";

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFileName))) {

            for (String path : BACKUP_PATHS) {
                File file = new File(path);
                if (file.exists()) {
                    if (file.isDirectory()) {
                        addDirectoryToZip(file, "", zos);
                    } else {
                        addFileToZip(file, "", zos);
                    }
                }
            }

            
            addBackupInfoFile(zos, timestamp);

            serverUI.log("[备份] 数据备份创建成功: " + backupFileName);
            return true;

        } catch (IOException e) {
            serverUI.log("[备份] 数据备份创建成功: " + backupFileName);
            return false;
        }
    }

    /**
     * 恢复数据备份
     */
    public boolean restoreBackup(String backupFilePath) {
        
        File backupFile = new File(backupFilePath);
        if (!backupFile.exists()) {
            serverUI.log("[恢复] 备份文件不存在: " + backupFilePath);
            return false;
        }

        
        String tempDir = "temp_restore_" + System.currentTimeMillis();
        File tempDirFile = new File(tempDir);

        try {
            
            extractZip(backupFilePath, tempDir);

            
            boolean wasServerRunning = serverUI.isServerRunning();
            boolean wasWebRunning = serverUI.isWebManagerRunning();

            if (wasServerRunning) {
                serverUI.stopServer();
            }
            if (wasWebRunning) {
                serverUI.stopWebManager();
            }

            
            Thread.sleep(2000);

            
            copyFiles(tempDir, ".");

            
            if (wasServerRunning) {
                Thread.sleep(1000);
                serverUI.startServer();
            }
            if (wasWebRunning) {
                Thread.sleep(1000);
                serverUI.startWebManager();
            }

            
            serverUI.loadUsers();
            serverUI.loadTokens();
            serverUI.loadActivationCodes();
            serverUI.loadBannedIps();

            serverUI.log("[恢复] 数据恢复成功");
            return true;

        } catch (Exception e) {
            serverUI.log("[恢复] 恢复备份失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            
            deleteDirectory(tempDirFile);
        }
    }

    /**
     * 添加目录到ZIP
     */
    private void addDirectoryToZip(File directory, String basePath, ZipOutputStream zos) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    addDirectoryToZip(file, basePath + file.getName() + File.separator, zos);
                } else {
                    addFileToZip(file, basePath, zos);
                }
            }
        }
    }

    /**
     * 添加文件到ZIP
     */
    private void addFileToZip(File file, String basePath, ZipOutputStream zos) throws IOException {
        String entryName = basePath + file.getName();
        ZipEntry zipEntry = new ZipEntry(entryName);
        zos.putNextEntry(zipEntry);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
        }
        zos.closeEntry();
    }

    /**
     * 添加备份信息文件
     */
    private void addBackupInfoFile(ZipOutputStream zos, String timestamp) throws IOException {
        ZipEntry infoEntry = new ZipEntry("backup_info.txt");
        zos.putNextEntry(infoEntry);

        String info = "备份时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n" +
                "备份版本: 1.0\n" +
                "包含文件: " + String.join(", ", BACKUP_PATHS) + "\n" +
                "系统: Helium Server ProMax Plus Boost";

        zos.write(info.getBytes());
        zos.closeEntry();
    }

    /**
     * 解压ZIP文件
     */
    private void extractZip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zis.getNextEntry();

            while (entry != null) {
                String filePath = destDirectory + File.separator + entry.getName();

                if (!entry.isDirectory()) {
                    extractFile(zis, filePath);
                } else {
                    File dir = new File(filePath);
                    dir.mkdirs();
                }

                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        }
    }

    /**
     * 解压单个文件
     */
    private void extractFile(ZipInputStream zis, String filePath) throws IOException {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
    }

    /**
     * 复制文件
     */
    private void copyFiles(String sourceDir, String destDir) throws IOException {
        File source = new File(sourceDir);
        File dest = new File(destDir);

        if (source.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }

            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(dest, file);
                    copyFiles(srcFile.getAbsolutePath(), destFile.getAbsolutePath());
                }
            }
        } else {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(dest)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        }
    }

    /**
     * 删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }

    /**
     * 获取备份文件列表
     */
    public File[] getBackupFiles() {
        File backupDir = new File(BACKUP_DIR);
        return backupDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
    }

    /**
     * 显示备份对话框
     */
    public void showBackupDialog() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        
        File[] backups = getBackupFiles();
        DefaultListModel<String> listModel = new DefaultListModel<>();
        if (backups != null) {
            for (File backup : backups) {
                listModel.addElement(backup.getName() + " (" +
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(backup.lastModified())) + ")");
            }
        }

        JList<String> backupList = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(backupList);

        panel.add(new JLabel("现有备份文件:"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton createBackupButton = new JButton("创建新备份");
        JButton restoreButton = new JButton("恢复选中备份");
        JButton deleteButton = new JButton("删除选中备份");

        createBackupButton.addActionListener(e -> {
            boolean success = createBackup();
            if (success) {
                JOptionPane.showMessageDialog(panel, "备份创建成功！", "成功",
                        JOptionPane.INFORMATION_MESSAGE);
                
                showBackupDialog();
            } else {
                JOptionPane.showMessageDialog(panel, "备份创建失败！", "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        restoreButton.addActionListener(e -> {
            int selectedIndex = backupList.getSelectedIndex();
            if (selectedIndex == -1) {
                JOptionPane.showMessageDialog(panel, "请先选择一个备份文件！", "提示",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            int result = JOptionPane.showConfirmDialog(panel,
                    "确定要恢复此备份吗？这将覆盖当前所有数据！", "确认恢复",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                File selectedBackup = backups[selectedIndex];
                boolean success = restoreBackup(selectedBackup.getAbsolutePath());
                if (success) {
                    JOptionPane.showMessageDialog(panel, "数据恢复成功！", "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(panel, "数据恢复失败！", "错误",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        deleteButton.addActionListener(e -> {
            int selectedIndex = backupList.getSelectedIndex();
            if (selectedIndex == -1) {
                JOptionPane.showMessageDialog(panel, "请先选择一个备份文件！", "提示",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            int result = JOptionPane.showConfirmDialog(panel,
                    "确定要删除此备份文件吗？", "确认删除",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                File selectedBackup = backups[selectedIndex];
                if (selectedBackup.delete()) {
                    JOptionPane.showMessageDialog(panel, "备份文件删除成功！", "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    
                    showBackupDialog();
                } else {
                    JOptionPane.showMessageDialog(panel, "备份文件删除失败！", "错误",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        buttonPanel.add(createBackupButton);
        buttonPanel.add(restoreButton);
        buttonPanel.add(deleteButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(serverUI, panel, "数据备份管理",
                JOptionPane.PLAIN_MESSAGE);
    }
}