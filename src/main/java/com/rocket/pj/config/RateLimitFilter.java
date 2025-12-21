package com.rocket.pj.config;

import com.rocket.pj.service.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;

    public RateLimitFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().equals("/login") && request.getMethod().equalsIgnoreCase("POST")) {
            String ip = request.getRemoteAddr();
            if (loginAttemptService.isBlocked(ip)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE,
                        HttpStatus.TOO_MANY_REQUESTS.value());
                request.getRequestDispatcher("/error").forward(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
