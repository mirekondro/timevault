# TimeVault

TimeVault currently contains two UI directions in the same repository:

- the root project is the Vaadin web app plus the shared backend code under `com.example.shared`
- the desktop JavaFX app now lives in its own `desktop-app/` project

## Desktop Project

```text
desktop-app/
|-- pom.xml
`-- src/
    `-- main/
        |-- java/com/example/desktop/
        `-- resources/
            |-- application.properties
            |-- db.example.properties
            |-- db.properties
            |-- com/example/desktop/gui/
            |-- desktop/styles.css
            `-- i18n/
```

The desktop app still follows the layered structure inside its own module:

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
./mvnw -f desktop-app/pom.xml javafx:run
```

From an IDE:

- import/open the `desktop-app` Maven project
- run `com.example.desktop.DesktopLauncher`

## Web App

The root project still contains the web app under `src/main/java/com/example/web` and uses the shared Spring/JPA backend from `src/main/java/com/example/shared`.

## Tech Stack

- Java 21
- JavaFX 21 in `desktop-app`
- Spring Boot 4.0.3 and Vaadin 25.0.7 in the root web app
- Microsoft SQL Server
- JDBC for the desktop data path
- Spring Data JPA for the web data path
