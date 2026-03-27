package dev.config;

import dev.security.LoginAttemptAuthenticationFailureHandler;
import dev.security.LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/", "/error", "/login", "/static/**").permitAll()
                .antMatchers("/server/**").hasRole("ADMIN")
                .antMatchers("/server/tokens/**", "/server/activation-codes/**").hasAnyRole("ADMIN", "TOKEN_ADMIN")
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .successHandler(loginSuccessHandler())
                .failureHandler(authenticationFailureHandler())
                .permitAll()
                .and()
                .logout()
                .permitAll()
                .and()
                .sessionManagement()
                .maximumSessions(1)
                .expiredUrl("/login?expired")
                .and()
                .sessionAuthenticationStrategy(sessionAuthenticationStrategy())
                .and()
                .csrf().disable();

        return http.build();
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    @Bean
    public LoginSuccessHandler loginSuccessHandler() {
        return new LoginSuccessHandler();
    }
    @Bean
    public SimpleUrlAuthenticationFailureHandler authenticationFailureHandler() {
        return new LoginAttemptAuthenticationFailureHandler();
    }
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {

        SessionFixationProtectionStrategy strategy = new SessionFixationProtectionStrategy();
        strategy.setMigrateSessionAttributes(true);
        return strategy;
    }
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
    @Bean
    public UserDetailsService userDetailsService() {
        String username = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String password = UUID.randomUUID().toString().substring(0, 12);
        System.out.println("==========================================");
        System.out.println("登录凭据");
        System.out.println("用户名: " + username);
        System.out.println("密码: " + password);
        System.out.println("==========================================");
        showCredentialsDialogNonBlocking(username, password);
        UserDetails user = User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    private void showCredentialsDialogNonBlocking(String username, String password) {

        new Thread(() -> {
            try {

                SwingUtilities.invokeAndWait(() -> createAndShowDialog(username, password));
            } catch (Exception e) {
                e.printStackTrace();

                System.err.println("无法显示登录凭据窗口，请查看控制台获取凭据信息");
            }
        }).start();
    }

    private void createAndShowDialog(String username, String password) {

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("当前环境不支持图形界面，无法显示登录凭据窗口");
            return;
        }
        JDialog dialog = new JDialog();
        dialog.setTitle("登录凭据");
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(400, 250);
        dialog.setResizable(false);
        dialog.setAlwaysOnTop(true);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation(screenSize.width/2 - dialog.getWidth()/2,
                screenSize.height/2 - dialog.getHeight()/2);
        Font yaheiFont = new Font("Microsoft YaHei", Font.PLAIN, 12);
        Font yaheiBold = new Font("Microsoft YaHei", Font.BOLD, 16);
        Font yaheiButton = new Font("Microsoft YaHei", Font.PLAIN, 11);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        JLabel titleLabel = new JLabel("您的登录凭据", JLabel.CENTER);
        titleLabel.setFont(yaheiBold);
        panel.add(titleLabel, BorderLayout.NORTH);
        JPanel credentialsPanel = new JPanel(new GridLayout(2, 2, 5, 10));
        credentialsPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));
        JLabel userLabel = new JLabel("用户名:");
        userLabel.setFont(yaheiFont);
        credentialsPanel.add(userLabel);
        JTextField userField = new JTextField(username);
        userField.setFont(yaheiFont);
        userField.setEditable(false);
        credentialsPanel.add(userField);
        JLabel passLabel = new JLabel("密码:");
        passLabel.setFont(yaheiFont);
        credentialsPanel.add(passLabel);
        JTextField passField = new JTextField(password);
        passField.setFont(yaheiFont);
        passField.setEditable(false);
        credentialsPanel.add(passField);
        panel.add(credentialsPanel, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton copyUserButton = new JButton("复制用户名");
        copyUserButton.setFont(yaheiButton);
        copyUserButton.addActionListener(e -> {
            StringSelection selection = new StringSelection(username);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            JOptionPane.showMessageDialog(dialog, "用户名已复制到剪贴板", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(copyUserButton);
        JButton copyPassButton = new JButton("复制密码");
        copyPassButton.setFont(yaheiButton);
        copyPassButton.addActionListener(e -> {
            StringSelection selection = new StringSelection(password);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            JOptionPane.showMessageDialog(dialog, "密码已复制到剪贴板", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(copyPassButton);
        JButton okButton = new JButton("确定");
        okButton.setFont(yaheiButton);
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        dialog.add(panel);
        dialog.setVisible(true);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent windowEvent) {

            }
        });
    }
}