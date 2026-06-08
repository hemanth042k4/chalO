package com.chalo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Forces Spring Security 6's deferred CSRF token to load before Thymeleaf starts
 * writing the response body.
 *
 * Without this, CsrfFilter stores a DeferredCsrfToken in the request attribute.
 * When Thymeleaf's th:action triggers getToken() ~12KB into the template (after
 * the CSS block has already filled Tomcat's 8KB response buffer and committed the
 * response), saveToken() tries to add a Set-Cookie header to an already-committed
 * response, causing an IllegalStateException that closes the connection mid-transfer.
 *
 * preHandle() runs before the controller method, before any response body is written.
 * Touching the token here creates the session and sets JSESSIONID while the response
 * is still uncommitted, so the later th:action call hits the cached token only.
 */
public class CsrfTokenHandlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        return true;
    }
}
