package com.example;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Web entry point for the Vaadin version of TimeVault.
 *
 * The repository is organized around one shared backend package used by both frontends:
 * `com.example.shared` contains the data model, repositories, and services.
 * `com.example.web` contains the Vaadin UI.
 * `com.example.desktop` contains the JavaFX UI.
 */
@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan("com.example.shared.model")
@EnableJpaRepositories("com.example.shared.repository")
@StyleSheet("styles.css")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
