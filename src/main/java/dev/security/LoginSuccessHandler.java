package dev.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;

    public LoginSuccessHandler() {
        this.loginAttemptService = new LoginAttemptService();

        setDefaultTargetUrl("/server");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, 
                                       Authentication authentication) throws ServletException, IOException {

        String remoteAddr = getClientIP(request);
        

        String username = authentication.getName();
        

        loginAttemptService.loginSucceeded(remoteAddr, username);
        

        super.onAuthenticationSuccess(request, response, authentication);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }

        return xfHeader.split(",")[0].trim();
    }
}