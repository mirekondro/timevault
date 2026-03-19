package com.example.support;

import java.nio.file.Path;

public record TimeVaultPaths(Path baseDir, Path databasePath, Path filesDir, Path exportsDir) {

    public static TimeVaultPaths defaultPaths() {
        Path baseDir = Path.of(System.getProperty("user.home"), ".timevault");
        return new TimeVaultPaths(
                baseDir,
                baseDir.resolve("timevault.db"),
                baseDir.resolve("files"),
                baseDir.resolve("exports")
        );
    }
}
