package edu.xjtlu.civa.server.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class AuthConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean AuthService authService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, PasswordEncoder passwords,
                                  @Value("${civa.auth.failure-limit:10}") int failureLimit,
                                  @Value("${civa.auth.failure-window-seconds:60}") long failureWindowSeconds) {
        return new AuthService(jdbc, new TransactionTemplate(transactionManager), passwords, Clock.systemUTC(),
                new SecureRandom(), failureLimit, Duration.ofSeconds(failureWindowSeconds));
    }
}
