package dev;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuiltInBrowserManager {
    private JFrame browserFrame;
    private JFXPanel jfxPanel;
    private WebEngine webEngine;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final String BROWSER_TITLE = "管理控制台";
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    private boolean javaFxInitialized = false;
    private final Object lock = new Object();

    public BuiltInBrowserManager() {

        initializeJavaFxPlatform();
    }

    private void initializeJavaFxPlatform() {
        if (!javaFxInitialized) {
            synchronized (lock) {
                if (!javaFxInitialized) {

                    new Thread(() -> {
                        SwingUtilities.invokeLater(() -> {
                            try {

                                new JFXPanel();
                                javaFxInitialized = true;
                                System.out.println("初始化成功");
                            } catch (Exception e) {
                                System.err.println("ERROR: " + e.getMessage());
                            }
                        });
                    }).start();
                }
            }
        }
    }

    public boolean isBrowserAvailable() {
        try {

            Class.forName("javafx.embed.swing.JFXPanel");
            Class.forName("javafx.scene.web.WebView");
            return true;
        } catch (ClassNotFoundException e) {
            System.err.println("浏览器组件不可用: 缺少必要的JavaFX库文件");
            return false;
        } catch (Exception e) {
            System.err.println("检查浏览器可用性时出错: " + e.getMessage());
            return false;
        }
    }

    public boolean startBrowser(final String url) {
        try {
            if (isRunning.get()) {

                SwingUtilities.invokeLater(() -> {
                    if (browserFrame != null) {
                        browserFrame.toFront();
                        browserFrame.requestFocus();
                    }
                    if (webEngine != null) {
                        Platform.runLater(() -> {
                            if (webEngine != null) {
                                webEngine.load(url);
                            }
                        });
                    }
                });
                return true;
            }


            SwingUtilities.invokeLater(() -> {
                try {

                    browserFrame = new JFrame(BROWSER_TITLE);
                    browserFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                    browserFrame.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
                    browserFrame.setLocationRelativeTo(null);


                    browserFrame.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {

                            stopBrowserAsync();
                        }
                    });


                    jfxPanel = new JFXPanel();
                    browserFrame.getContentPane().add(jfxPanel, BorderLayout.CENTER);


                    Platform.runLater(() -> {
                        try {

                            WebView webView = new WebView();
                            webEngine = webView.getEngine();


                            configureWebEngine(webEngine);


                            Scene scene = new Scene(webView);
                            jfxPanel.setScene(scene);


                            webEngine.load(url);


                            browserFrame.setVisible(true);

                            isRunning.set(true);
                        } catch (Exception ex) {
                            System.err.println("初始化WebView时出错: " + ex.getMessage());
                            ex.printStackTrace();

                            cleanupResources();
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();

                    cleanupResources();
                }
            });


            return true;
        } catch (Exception e) {
            System.err.println("启动浏览器失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void configureWebEngine(WebEngine webEngine) {
        try {

            webEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            

            configurePerformanceSettings();
            

            String css = "* { font-family: 'Microsoft YaHei', '微软雅黑', sans-serif !important; }\n" +
                         "::-webkit-scrollbar {\n" +
                         "    width: 8px;\n" +
                         "    height: 8px;\n" +
                         "}\n" +
                         "::-webkit-scrollbar-track {\n" +
                         "    background: #f1f1f1;\n" +
                         "    border-radius: 4px;\n" +
                         "}\n" +
                         "::-webkit-scrollbar-thumb {\n" +
                         "    background: #888;\n" +
                         "    border-radius: 4px;\n" +
                         "}\n" +
                         "::-webkit-scrollbar-thumb:hover {\n" +
                         "    background: #555;\n" +
                         "}\n" +
                         "* {\n" +
                         "    scrollbar-width: thin;\n" +
                         "    scrollbar-color: #888 #f1f1f1;\n" +
                         "}\n" +
                         "body { -webkit-font-smoothing: antialiased; }\n" +
                         "img { image-rendering: -webkit-optimize-contrast; }";
            

            String encodedCss = java.net.URLEncoder.encode(css, "UTF-8");
            java.net.URI uri = java.net.URI.create("data:text/css;charset=utf-8," + encodedCss);
            webEngine.setUserStyleSheetLocation(uri.toString());
        } catch (Exception e) {
        }


        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            switch (newState) {
                case SUCCEEDED:
                    break;
                case FAILED:

                    Throwable exception = webEngine.getLoadWorker().getException();
                    if (exception != null) {
                        System.err.println("ERROR: " + exception.getMessage());
                    }
                    break;
                case CANCELLED:
                    break;
            }
        });


        webEngine.setJavaScriptEnabled(true);
        try {

            System.setProperty("com.sun.webkit.useFastJavaScript", "true");
        } catch (Exception e) {
        }


        com.sun.javafx.webkit.WebConsoleListener.setDefaultListener((webView, message, lineNumber, sourceId) -> {
            System.out.println("[WebConsole] " + message + " (" + sourceId + ":" + lineNumber + ")");
        });


        try {

            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };

            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.err.println("配置HTTPS证书验证失败: " + e.getMessage());
        }
    }

    private void configurePerformanceSettings() {
        try {

            System.setProperty("prism.forceGPU", "true");
            System.setProperty("prism.order", "d3d,sw");
            

            System.setProperty("prism.lcdtext", "false");
            System.setProperty("prism.cachelevel", "Glyph");
            

            System.setProperty("com.sun.webkit.graphics.UseRefCountedMemory", "true");
            

            System.setProperty("com.sun.webkit.imageDecoderThreads", "2");
            

            System.setProperty("com.sun.webkit.perf", "false");

        } catch (Exception e) {
        }
    }

    public void stopBrowser() {
        stopBrowserAsync();
    }
    private void stopBrowserAsync() {
        if (!isRunning.get()) {
            return;
        }


        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                try {

                    Platform.runLater(() -> {
                        try {
                            if (webEngine != null) {
                                webEngine.load(null);
                                webEngine = null;
                            }
                        } catch (Exception ex) {
                            System.err.println("WebEngine: " + ex.getMessage());
                        }
                    });


                    if (jfxPanel != null) {
                        jfxPanel.removeAll();
                        jfxPanel = null;
                    }

                    if (browserFrame != null) {
                        browserFrame.dispose();
                        browserFrame = null;
                    }

                    isRunning.set(false);
                } catch (Exception ex) {
                    System.err.println("ERROR: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        }).start();
    }
    private void cleanupResources() {
        try {
            if (webEngine != null) {

                webEngine.load(null);

                try {
                    Class webPageClass = Class.forName("com.sun.webkit.WebPage");
                    java.lang.reflect.Method clearCacheMethod = webPageClass.getMethod("clearCache");
                    clearCacheMethod.invoke(null);
                } catch (Exception e) {

                }
                webEngine = null;
            }
            

            System.gc();
            
            if (jfxPanel != null) {
                jfxPanel.removeAll();
                jfxPanel = null;
            }
            if (browserFrame != null) {
                browserFrame.dispose();
                browserFrame = null;
            }
            isRunning.set(false);
            
            System.out.println("已完全清理浏览器资源");
        } catch (Exception e) {
            System.err.println("清理浏览器资源时出错: " + e.getMessage());
        }
    }

    public boolean isBrowserRunning() {
        return isRunning.get();
    }
    public void shutdown() {
        stopBrowserAsync();

    }
}