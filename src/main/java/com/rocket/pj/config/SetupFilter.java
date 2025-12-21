package com.rocket.pj.config;

import com.rocket.pj.repository.SystemConfigRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SetupFilter implements Filter {

    private final SystemConfigRepository systemConfigRepository;

    public SetupFilter(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // Allow static resources
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/favicon.ico")) {
            chain.doFilter(request, response);
            return;
        }

        boolean isSetup = systemConfigRepository.findById(1L).map(config -> config.isSetupComplete()).orElse(false);

        if (!isSetup) {
            // If not setup, only allow /setup
            if (!path.equals("/setup")) {
                httpResponse.sendRedirect("/setup");
                return;
            }
        } else {
            // If setup, prevent access to /setup (unless we want to allow re-setup? No,
            // should use factory reset)
            // But wait, if we redirect /setup to /, we might loop if / is protected and
            // user not logged in.
            // Security filter chain handles auth. This filter handles setup state.
            // If setup is done, and user goes to /setup, redirect to /login or /
            if (path.equals("/setup")) {
                httpResponse.sendRedirect("/login");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
