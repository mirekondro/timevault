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
 * The repository is organized around a shared backend package plus separate UI projects:
 * `com.example.shared` contains the data model, repositories, and services used by the web app.
 * `com.example.web` contains the Vaadin UI in this root project.
 * The JavaFX desktop UI now lives in the separate `desktop-app` module.
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
