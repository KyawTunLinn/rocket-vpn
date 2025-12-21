package com.rocket.pj.config;

import com.rocket.pj.entity.User;
import com.rocket.pj.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Default user creation is handled by Setup Wizard
            // if (userRepository.findByUsername("admin").isEmpty()) {
            // User admin = new User("admin", passwordEncoder.encode("password"), "ADMIN");
            // userRepository.save(admin);
            // }
        };
    }
}
