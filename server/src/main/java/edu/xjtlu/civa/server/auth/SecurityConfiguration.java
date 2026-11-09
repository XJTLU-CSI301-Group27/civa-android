package edu.xjtlu.civa.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    static final String TRACE_ID = SecurityConfiguration.class.getName() + ".traceId";

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService auth, ObjectMapper json) throws Exception {
        ErrorWriter errors = new ErrorWriter(json);
        http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errorsConfig -> errorsConfig
                        .authenticationEntryPoint((request, response, ex) -> errors.write(request, response, 401, "UNAUTHENTICATED", "Authentication required"))
                        .accessDeniedHandler((request, response, ex) -> errors.write(request, response, 403, "FORBIDDEN", "Access denied")))
                .addFilterBefore(new TraceFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new BearerFilter(auth, errors), TraceFilter.class);
        return http.build();
    }

    private static final class TraceFilter extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            request.setAttribute(TRACE_ID, UUID.randomUUID().toString());
            chain.doFilter(request, response);
        }
    }

    private static final class BearerFilter extends OncePerRequestFilter {
        private final AuthService auth;
        private final ErrorWriter errors;
        private BearerFilter(AuthService auth, ErrorWriter errors) { this.auth = auth; this.errors = errors; }

        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                try {
                    AuthService.User user = auth.authenticatedUser(header.substring(7).trim());
                    if (user != null) {
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.id, null, List.of()));
                        SecurityContextHolder.setContext(context);
                    }
                } catch (DataAccessException ex) {
                    errors.write(request, response, 500, "INTERNAL_ERROR", "Request failed");
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }

    private static final class ErrorWriter {
        private final ObjectMapper json;
        private ErrorWriter(ObjectMapper json) { this.json = json; }
        void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException {
            response.setStatus(status);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            String traceId = (String) request.getAttribute(TRACE_ID);
            if (traceId == null) traceId = UUID.randomUUID().toString();
            json.writeValue(response.getOutputStream(), Map.of("code", code, "message", message, "traceId", traceId, "fieldErrors", List.of()));
        }
    }
}
