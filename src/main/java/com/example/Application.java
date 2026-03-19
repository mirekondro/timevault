package com.example;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║                         TIMEVAULT APPLICATION                              ║
 * ╠═══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  PROJECT STRUCTURE:                                                        ║
 * ║                                                                            ║
 * ║  com.example.shared.*     → SHARED BACKEND (both versions use this)       ║
 * ║    ├── model/             → Database entities (VaultItem)                  ║
 * ║    ├── repository/        → JPA repositories                              ║
 * ║    └── service/           → Business logic services                       ║
 * ║                                                                            ║
 * ║  com.example.web.*        → WEB VERSION (Vaadin) - YOUR PART              ║
 * ║    └── views/             → Vaadin views (MainView)                       ║
 * ║                                                                            ║
 * ║  com.example.desktop.*    → DESKTOP VERSION (JavaFX) - FRIEND'S PART      ║
 * ║    └── (implement here)   → JavaFX controllers and views                 ║
 * ║                                                                            ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 */
@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan("com.example.shared.model")
@EnableJpaRepositories("com.example.shared.repository")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
