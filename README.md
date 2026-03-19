# TimeVault

TimeVault currently contains two UI directions in the same repository:

- a Vaadin web app that still uses the Spring/JPA shared backend
- a desktop-first JavaFX app that now uses a classic layered structure with `dao`, `bll`, `gui`, and an observable `AppModel`

## Desktop Structure

```text
src/main/java/com/example/desktop/
|-- DesktopApp.java                  # JavaFX bootstrap
|-- DesktopLauncher.java             # IDE-friendly launcher
|-- bll/
|   `-- VaultManager.java            # Business logic
|-- dao/
|   |-- DatabaseConfig.java          # Loads db.properties
|   |-- ConnectionManager.java       # SQL Server connections
|   |-- SchemaInitializer.java       # Ensures table exists
|   |-- VaultItemDAO.java            # DAO contract
|   `-- SqlVaultItemDAO.java         # SQL Server DAO implementation
|-- gui/
|   |-- MainController.java
|   |-- TopBarController.java
|   |-- SaveController.java
|   |-- ArchiveController.java
|   |-- DetailController.java
|   |-- VaultItemCell.java
|   `-- AppContextAware.java
`-- model/
    |-- AppModel.java                # Shared observable application state
    `-- VaultItemFx.java             # Observable item model
```

## Desktop FXML

```text
src/main/resources/com/example/desktop/gui/
|-- main-view.fxml
|-- top-bar.fxml
|-- save-view.fxml
|-- archive-view.fxml
`-- detail-view.fxml
```

Controllers do not talk to each other directly. They only:

- bind to `AppModel`
- call `VaultManager`
- react to observable state changes

## Desktop Flow

1. `DesktopLauncher` starts `DesktopApp`
2. `DesktopApp` creates the DAO, BLL, and `AppModel`
3. `DesktopApp` loads `main-view.fxml`
4. `MainController` injects the shared context into child controllers
5. `VaultManager` initializes the schema and loads the first data set
6. All GUI updates then happen through observable properties in `AppModel`

## Database Configuration

Desktop database settings come from:

- `src/main/resources/db.properties` for your real local credentials
- `src/main/resources/db.example.properties` as the safe template

The desktop app uses Microsoft SQL Server through the Microsoft JDBC driver and creates `dbo.vault_items` if it does not already exist. If `db.resetOnStart=true`, the table is recreated on startup.

## Run The Desktop App

From Maven:

```bash
./mvnw javafx:run
```

From an IDE:

- run `com.example.desktop.DesktopLauncher`

## Web App

The web app is still present under `src/main/java/com/example/web` and uses the Spring/JPA path from `com.example.shared`. I did not remove it, but the new architectural work in this pass is focused on the desktop app.

## Tech Stack

- Java 21
- JavaFX 21
- FXML
- Microsoft SQL Server
- Java JDBC DAO layer
- JavaFX observable AppModel
- Spring Boot 4.0.3 and Vaadin 25.0.7 for the separate web side
