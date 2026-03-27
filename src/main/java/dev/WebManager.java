package dev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication(scanBasePackages = {"dev"})
public class WebManager {
    
    private ConfigurableApplicationContext context;
    private static volatile WebManager instance;
    private int webPort = 443;
    private int httpsPort = 443;
    private boolean useHttps = true;
    private String keystorePath = "ssl/keystore.p12";
    private String keystorePassword = "changeit";
    private String keyPassword = "changeit";
    private String keystoreType = "PKCS12";
    

    private ConcurrentHashMap<String, Boolean> validTokens = new ConcurrentHashMap<>();
    

    public WebManager() {

        if (instance == null) {
            synchronized (WebManager.class) {
                if (instance == null) {
                    instance = this;
                }
            }
        }
    }
    

    public static WebManager getInstance() {
        if (instance == null) {
            synchronized (WebManager.class) {
                if (instance == null) {

                    instance = new WebManager();
                }
            }
        }
        return instance;
    }

    public void startWebServer() {
        if (context != null && context.isActive()) {
            System.out.println("Web服务器已经在运行中");
            return;
        }

        try {
            File sslDir = new File("ssl");
            if (!sslDir.exists()) {
                sslDir.mkdirs();
            }
            SpringApplicationBuilder builder = new SpringApplicationBuilder(WebManager.class);
            builder.properties("server.address=0.0.0.0");

            if (useHttps) {
                File keystoreFile = new File(keystorePath);
                if (!keystoreFile.exists()) {
                    System.out.println("警告: SSL证书文件不存在: " + keystorePath);
                    System.out.println("请手动创建SSL证书并放置在指定位置。");
                    System.out.println("创建自签名证书的命令示例（在命令行中执行）:");
                    System.out.println("keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore " + keystorePath + " -validity 365 -storepass changeit");
                    System.out.println("提示: 运行上述命令后，按照提示填写证书信息，Common Name建议设为'localhost'");
                    System.out.println("由于证书不存在，将使用HTTP协议启动Web服务器");
                    builder.properties("server.port=" + webPort);
                    System.out.println("Web管理器启动成功，本地访问地址: http://localhost:" + webPort + "/server");
                    System.out.println("局域网/公网访问地址: http://本机IP地址:" + webPort + "/server");
                } else {
                    builder.properties(
                            "server.port=" + httpsPort,
                            "server.ssl.key-store=" + keystorePath,
                            "server.ssl.key-store-password=" + keystorePassword,
                            "server.ssl.key-password=" + keyPassword,
                            "server.ssl.key-store-type=" + keystoreType
                    );
                    System.out.println("Web管理器启动成功，本地访问地址: https://localhost:" + httpsPort + "/server");
                    System.out.println("局域网/公网访问地址: https://本机IP地址:" + httpsPort + "/server");
                }
            } else {
                builder.properties("server.port=" + webPort);
                System.out.println("Web管理器启动成功，本地访问地址: http://localhost:" + webPort + "/server");
                System.out.println("局域网/公网访问地址: http://本机IP地址:" + webPort + "/server");
            }
            context = builder.run();

        } catch (Exception e) {
            System.out.println("Web服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopWebServer() {
        if (context != null && context.isActive()) {
            SpringApplication.exit(context, () -> 0);
            System.out.println("Web服务器已停止");
        }
    }
    
    public boolean isRunning() {
        return context != null && context.isActive();
    }
    
    public void setWebPort(int port) {
        this.webPort = port;

        if (isRunning()) {
            stopWebServer();
            startWebServer();
        }
    }
    
    public int getWebPort() {
        return webPort;
    }
    
    public int getPort() {
        return webPort;
    }
    

    public int getHttpsPort() {
        return httpsPort;
    }
    

    public boolean isUseHttps() {
        return useHttps;
    }
    

    public String getKeystorePath() {
        return keystorePath;
    }
}