# TimeVault

TimeVault is now organized as a small multi-module repository:

- `shared-core/` contains the shared models, repositories, security helpers, and services
- `web-app/` contains the Vaadin/Spring web application
- `desktop-app/` contains the JavaFX desktop application
- the root `pom.xml` is now a parent/aggregator for the modules above

## Project Layout

```text
timevault/
|-- pom.xml
|-- shared-core/
|-- web-app/
`-- desktop-app/
```

## Desktop Project

The desktop app follows a layered structure inside its own module:

- `dao` for SQL Server access and schema setup
- `bll` for application workflows
- `gui` for JavaFX controllers
- `model` for observable UI state

Controllers do not talk to each other directly. They:

- bind to `AppModel`
- call `VaultManager`
- react to observable state changes

## Desktop Flow

1. `DesktopLauncher` starts `DesktopApp`
2. `DesktopApp` creates the DAO, BLL, and `AppModel`
3. `DesktopApp` loads `main-view.fxml`
4. `MainController` injects the shared context into child controllers
5. `VaultManager` initializes the schema and loads the first data set
6. GUI updates happen through observable properties in `AppModel`

## Desktop Configuration

Desktop database settings now live in:

- `desktop-app/src/main/resources/db.properties` for real local credentials
- `desktop-app/src/main/resources/db.example.properties` as the safe template

Desktop Gemini settings live in:

- `desktop-app/src/main/resources/application.properties`

The desktop app uses Microsoft SQL Server through JDBC and creates `dbo.vault_items` if it does not already exist. If `db.resetOnStart=true`, the desktop tables are dropped, recreated, and reseeded on startup.

## Run The Desktop App

From Maven:

```bash
./mvnw -pl desktop-app -am javafx:run
```

From an IDE:

- open the root multi-module workspace or Maven project
- run `com.example.desktop.DesktopLauncher`

## Web App

The web app now lives in `web-app/` and depends on `shared-core/` for the shared Spring/JPA backend code.

Run it from Maven with:

```bash
./mvnw -pl web-app -am spring-boot:run
```

## Tech Stack

- Maven multi-module build
- Java 21
- Shared core module for common backend/domain code
- JavaFX 21 in `desktop-app`
- Spring Boot 4.0.3 and Vaadin 25.0.7 in `web-app`
- Microsoft SQL Server
- JDBC for the desktop data path
- Spring Data JPA for the web data path
