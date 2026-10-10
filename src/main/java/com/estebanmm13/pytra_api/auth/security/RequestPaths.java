package com.estebanmm13.pytra_api.auth.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Path matching helpers for servlet filters that make security decisions by URL.
 */
final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * The request path inside the application: {@code servletPath + pathInfo}.
     * <p>
     * Unlike {@code getRequestURI()}, the container has already percent-decoded it, normalized {@code .}/{@code ..}
     * segments and stripped {@code ;} path parameters, so it is the path Spring MVC actually routes on. It is also
     * independent of the context path, which ForwardedHeaderFilter rewrites from the client-controlled
     * {@code X-Forwarded-Prefix} header (that header changes {@code getContextPath()}/{@code getRequestURI()}, not
     * {@code getServletPath()}).
     */
    static String pathWithinApplication(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        return (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
    }
}
