package dev.security;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class LoginAttemptAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public LoginAttemptAuthenticationFailureHandler() {
        this.loginAttemptService = new LoginAttemptService();

        setDefaultFailureUrl("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, 
                                       AuthenticationException exception) throws IOException, ServletException {

        String remoteAddr = getClientIP(request);
        String username = request.getParameter("username");
        

        loginAttemptService.loginFailed(remoteAddr, username);
        

        if (loginAttemptService.isBlocked(remoteAddr) || 
            (username != null && loginAttemptService.isUserBlocked(username))) {
            exception = new LockedException("账户或IP已被临时锁定，请稍后再试");
        }
        
        super.onAuthenticationFailure(request, response, exception);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }

        return xfHeader.split(",")[0].trim();
    }
}