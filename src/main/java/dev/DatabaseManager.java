
package dev;

import org.mindrot.jbcrypt.BCrypt;

import javax.swing.*;
import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:h2:file:./database/users;AUTO_SERVER=TRUE;CIPHER=AES";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "filepass userpass";

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        initializeDatabase();
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    private User encryptUserData(User user) {
        try {
            User encryptedUser = new User();
            encryptedUser.setUsername(user.getUsername());
            encryptedUser.setEmail(user.getEmail());
            encryptedUser.setPassword(user.getPassword()); 

            
            if (user.getHwid() != null && !user.getHwid().isEmpty()) {
                String encryptedHwid = DataEncryptionUtil.encryptHwid(user.getHwid());
                if (encryptedHwid != null) {
                    encryptedUser.setHwid(encryptedHwid);
                } else {
                    
                    System.err.println("HWID加密失败，使用原始数据: " + user.getUsername());
                    encryptedUser.setHwid(user.getHwid());
                }
            }

            if (user.getRegisterIp() != null && !user.getRegisterIp().isEmpty()) {
                String encryptedIp = DataEncryptionUtil.encryptIp(user.getRegisterIp());
                if (encryptedIp != null) {
                    encryptedUser.setRegisterIp(encryptedIp);
                } else {
                    
                    System.err.println("IP加密失败，使用原始数据: " + user.getUsername());
                    encryptedUser.setRegisterIp(user.getRegisterIp());
                }
            }

            encryptedUser.setCurrentParam(user.getCurrentParam());
            encryptedUser.setTokenCode(user.getTokenCode());
            encryptedUser.setRole(user.getRole());

            return encryptedUser;
        } catch (Exception e) {
            System.err.println("加密用户数据失败: " + e.getMessage());
            return user; 
        }
    }

    
    private User decryptUserData(User user) {
        try {
            User decryptedUser = new User();
            decryptedUser.setUsername(user.getUsername());
            decryptedUser.setEmail(user.getEmail());
            decryptedUser.setPassword(user.getPassword());

            
            if (user.getHwid() != null && !user.getHwid().isEmpty()) {
                String decryptedHwid = DataEncryptionUtil.decryptHwid(user.getHwid());
                if (decryptedHwid != null) {
                    decryptedUser.setHwid(decryptedHwid);
                } else {
                    
                    System.err.println("HWID解密失败，使用原始数据: " + user.getUsername());
                    decryptedUser.setHwid(user.getHwid());
                }
            }

            if (user.getRegisterIp() != null && !user.getRegisterIp().isEmpty()) {
                String decryptedIp = DataEncryptionUtil.decryptIp(user.getRegisterIp());
                if (decryptedIp != null) {
                    decryptedUser.setRegisterIp(decryptedIp);
                } else {
                    
                    System.err.println("IP解密失败，使用原始数据: " + user.getUsername());
                    decryptedUser.setRegisterIp(user.getRegisterIp());
                }
            }

            decryptedUser.setCurrentParam(user.getCurrentParam());
            decryptedUser.setTokenCode(user.getTokenCode());
            decryptedUser.setRole(user.getRole());

            return decryptedUser;
        } catch (Exception e) {
            System.err.println("解密用户数据失败: " + e.getMessage());
            return user; 
        }
    }


    
    public Map<String, Object> getUserStatistics() {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT COUNT(*) as total, " +
                "COUNT(CASE WHEN role = 'ADMIN' THEN 1 END) as admins, " +
                "COUNT(CASE WHEN hwid IS NOT NULL THEN 1 END) as hwid_bound " +
                "FROM users";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                stats.put("totalUsers", rs.getInt("total"));
                stats.put("adminUsers", rs.getInt("admins"));
                stats.put("hwidBoundUsers", rs.getInt("hwid_bound"));
            }
        } catch (SQLException e) {
            System.err.println("获取用户统计失败: " + e.getMessage());
        }
        return stats;
    }

    
    public List<User> searchUsers(String keyword) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE username LIKE ? OR email LIKE ? OR hwid LIKE ? ORDER BY created_at DESC";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            String likeKeyword = "%" + keyword + "%";
            pstmt.setString(1, likeKeyword);
            pstmt.setString(2, likeKeyword);
            pstmt.setString(3, likeKeyword);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                users.add(resultSetToUser(rs));
            }
        } catch (SQLException e) {
            System.err.println("搜索用户失败: " + e.getMessage());
        }
        return users;
    }

    
    public boolean deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("删除用户失败: " + e.getMessage());
            return false;
        }
    }
    
    public boolean deleteUsers(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return true;
        }

        String placeholders = String.join(",", Collections.nCopies(usernames.size(), "?"));
        String sql = "DELETE FROM users WHERE username IN (" + placeholders + ")";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            for (int i = 0; i < usernames.size(); i++) {
                pstmt.setString(i + 1, usernames.get(i));
            }

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("批量删除用户失败: " + e.getMessage());
            return false;
        }
    }

    
    public boolean updateUserRole(String username, String role) {
        String sql = "UPDATE users SET role = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, role);
            pstmt.setString(2, username);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("更新用户角色失败: " + e.getMessage());
            return false;
        }
    }

    
    public boolean resetUserHwid(String username) {
        String sql = "UPDATE users SET hwid = NULL, updated_at = CURRENT_TIMESTAMP WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("重置用户HWID失败: " + e.getMessage());
            return false;
        }
    }

    
    public Map<String, Object> getDatabaseInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            DatabaseMetaData metaData = getConnection().getMetaData();
            info.put("databaseProduct", metaData.getDatabaseProductName());
            info.put("databaseVersion", metaData.getDatabaseProductVersion());
            info.put("driverName", metaData.getDriverName());
            info.put("driverVersion", metaData.getDriverVersion());

            
            String sql = "SELECT COUNT(*) as user_count FROM users";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    info.put("userCount", rs.getInt("user_count"));
                }
            }

        } catch (SQLException e) {
            System.err.println("获取数据库信息失败: " + e.getMessage());
        }
        return info;
    }


    private void initializeDatabase() {
        try {
            if (!DataEncryptionUtil.isKeySet()) {
                throw new IllegalStateException("加密密钥未设置，无法初始化数据库");
            }

            
            Class.forName("org.h2.Driver");

            Properties props = new Properties();
            props.setProperty("user", DB_USER);
            props.setProperty("password", DB_PASSWORD);
            props.setProperty("CIPHER", "AES");

            connection = DriverManager.getConnection(DB_URL, props);
            createTables();
            System.out.println("数据库初始化成功");
        } catch (Exception e) {
            System.err.println("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "数据库初始化失败: " + e.getMessage() + "\n程序将退出。",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
        }
    }

    private void createTables() {
        String createUserTable = """
        CREATE TABLE IF NOT EXISTS users (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            username VARCHAR(255) UNIQUE NOT NULL,
            email VARCHAR(255),
            password_hash VARCHAR(255) NOT NULL,
            hwid VARCHAR(512),
            current_param VARCHAR(255) DEFAULT '3_mode',
            token_code VARCHAR(255) DEFAULT 'public',
            role VARCHAR(50) DEFAULT 'USER',
            register_ip VARCHAR(100),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUserTable);
            System.out.println("用户表创建中");

            try {
                stmt.execute("CREATE INDEX idx_username ON users(username)");
                System.out.println("用户名索引创建成功");
            } catch (SQLException e) {
                System.out.println("用户名索引已存在或创建失败: " + e.getMessage());
                System.out.println("文件存在的情况下请忽略该警告");
            }

            try {
                stmt.execute("CREATE INDEX idx_hwid ON users(hwid)");
                System.out.println("HWID索引创建成功");
            } catch (SQLException e) {
                System.out.println("HWID索引已存在或创建失败: " + e.getMessage());
                System.out.println("文件存在的情况下请忽略该警告");
            }

            try {
                stmt.execute("CREATE INDEX idx_token_code ON users(token_code)");
                System.out.println("标识码索引创建成功");
            } catch (SQLException e) {
                System.out.println("标识码索引已存在或创建失败: " + e.getMessage());
                System.out.println("文件存在的情况下请忽略该警告");
            }

        } catch (SQLException e) {
            System.err.println("创建用户表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initializeDatabase();
            }
        } catch (SQLException e) {
            System.err.println("获取数据库连接失败: " + e.getMessage());
        }
        return connection;
    }

    public boolean addUser(User user) {
        String sql = "INSERT INTO users (username, email, password_hash, hwid, current_param, token_code, role, register_ip) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {

            User encryptedUser = encryptUserData(user);

            pstmt.setString(1, encryptedUser.getUsername());
            pstmt.setString(2, encryptedUser.getEmail());
            pstmt.setString(3, BCrypt.hashpw(user.getPassword(), BCrypt.gensalt())); 
            pstmt.setString(4, encryptedUser.getHwid());
            pstmt.setString(5, encryptedUser.getCurrentParam());
            pstmt.setString(6, encryptedUser.getTokenCode());
            pstmt.setString(7, encryptedUser.getRole());
            pstmt.setString(8, encryptedUser.getRegisterIp());

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("添加用户失败: " + e.getMessage());
            return false;
        }
    }

    
    public User getUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return resultSetToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("根据用户名获取用户失败: " + e.getMessage());
        }
        return null;
    }

    
    public User getUserByHwid(String hwid) {
        String sql = "SELECT * FROM users WHERE hwid = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, hwid);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return resultSetToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("根据HWID获取用户失败: " + e.getMessage());
        }
        return null;
    }

    
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY created_at DESC";

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(resultSetToUser(rs));
            }
        } catch (SQLException e) {
            System.err.println("获取所有用户失败: " + e.getMessage());
        }
        return users;
    }

    
    public boolean updateUser(User user) {
        String sql = """
        UPDATE users SET 
        email = ?, hwid = ?, current_param = ?, token_code = ?, role = ?, register_ip = ?, updated_at = CURRENT_TIMESTAMP 
        WHERE username = ?
        """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            
            User encryptedUser = encryptUserData(user);

            pstmt.setString(1, encryptedUser.getEmail());
            pstmt.setString(2, encryptedUser.getHwid());
            pstmt.setString(3, encryptedUser.getCurrentParam());
            pstmt.setString(4, encryptedUser.getTokenCode());
            pstmt.setString(5, encryptedUser.getRole());
            pstmt.setString(6, encryptedUser.getRegisterIp());
            pstmt.setString(7, user.getUsername());

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("更新用户失败: " + e.getMessage());
            return false;
        }
    }

    
    public boolean updateUserPassword(String username, String newPassword) {
        String sql = "UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, BCrypt.hashpw(newPassword, BCrypt.gensalt()));
            pstmt.setString(2, username);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("更新用户密码失败: " + e.getMessage());
            return false;
        }
    }

    
    public boolean updateUserHwid(String username, String hwid) {
        String sql = "UPDATE users SET hwid = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            
            String encryptedHwid = DataEncryptionUtil.encryptHwid(hwid);

            pstmt.setString(1, encryptedHwid);
            pstmt.setString(2, username);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("更新用户HWID失败: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("数据库连接已关闭");
            }
        } catch (SQLException e) {
            System.err.println("关闭数据库连接失败: " + e.getMessage());
        }
    }

    
    public boolean verifyPassword(String username, String password) {
        User user = getUserByUsername(username);
        if (user == null) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, user.password);
        } catch (Exception e) {
            System.err.println("密码验证失败: " + e.getMessage());
            return false;
        }
    }

    private User resultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password_hash"));
        user.setHwid(rs.getString("hwid"));
        user.setCurrentParam(rs.getString("current_param"));
        user.setTokenCode(rs.getString("token_code"));
        user.setRole(rs.getString("role"));
        user.setRegisterIp(rs.getString("register_ip"));

        return decryptUserData(user);
    }

}