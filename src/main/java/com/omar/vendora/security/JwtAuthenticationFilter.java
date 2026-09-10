package com.omar.vendora.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();

        if (token.isEmpty() || !jwtService.validateToken(token)) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String subject = jwtService.extractSubject(token);
            Map<String, Object> claims = jwtService.extractClaims(token);

            List<GrantedAuthority> authorities = new ArrayList<>();
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List<?> roleList) {
                for (Object role : roleList) {
                    if (role != null) {
                        String roleStr = role.toString();
                        if (!roleStr.startsWith("ROLE_")) {
                            roleStr = "ROLE_" + roleStr;
                        }
                        authorities.add(new SimpleGrantedAuthority(roleStr));
                    }
                }
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                subject,
                null,
                authorities
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
