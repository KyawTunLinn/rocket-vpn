package com.rocket.pj.config;

import com.rocket.pj.service.LoginAttemptService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationListeners {

    private final LoginAttemptService loginAttemptService;

    public AuthenticationListeners(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Object details = event.getAuthentication().getDetails();
        if (details instanceof WebAuthenticationDetails) {
            String ip = ((WebAuthenticationDetails) details).getRemoteAddress();
            loginAttemptService.loginSucceeded(ip);
        }
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        Object details = event.getAuthentication().getDetails();
        if (details instanceof WebAuthenticationDetails) {
            String ip = ((WebAuthenticationDetails) details).getRemoteAddress();
            loginAttemptService.loginFailed(ip);
        }
    }
}
