# TimeVault

TimeVault is a local-first application for Hack Esbjerg 2026. It captures URLs, text, and images into a SQL Server database, writes a three-sentence AI context note, and auto-tags content.

## 🏗️ Project Structure

```
src/main/java/com/example/
│
├── Application.java           # Spring Boot entry point
│
├── shared/                    # ⭐ SHARED BACKEND (both versions use this)
│   ├── model/
│   │   └── VaultItem.java     # Database entity
│   ├── repository/
│   │   └── VaultItemRepository.java  # JPA repository
│   └── service/
│       └── VaultItemService.java     # Business logic
│
├── web/                       # 🌐 WEB VERSION (Vaadin) - Miroslav
│   └── views/
│       └── MainView.java      # Web UI
│
└── desktop/                   # 🖥️ DESKTOP VERSION (JavaFX) - Friend
    └── (implement here)       # JavaFX app
```

## 👥 Team Division

| Part | Owner | Technology | Folder |
|------|-------|------------|--------|
| **Shared Backend** | Both | Spring Data JPA, MS SQL | `shared/` |
| **Web Frontend** | Miroslav | Vaadin 25 | `web/` |
| **Desktop Frontend** | Friend | JavaFX | `desktop/` |

## 🚀 Run Web Version

```bash
./mvnw spring-boot:run
```

Then open: **http://localhost:8080**

## 🔧 Configuration

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=School;encrypt=true;trustServerCertificate=true
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

## 📦 Using Shared Backend

Both web and desktop versions use the same `VaultItemService`:

```java
@Autowired
private VaultItemService vaultItemService;

// Save URL
vaultItemService.saveUrl(url, title, content, aiContext);

// Save text
vaultItemService.saveText(title, content, aiContext);

// Save image
vaultItemService.saveImage(title, imagePath, aiContext);

// Get recent items
List<VaultItem> items = vaultItemService.findRecent();

// Search
List<VaultItem> results = vaultItemService.search("keyword");

// Delete
vaultItemService.delete(itemId);
```

## 🗄️ Database (MS SQL Server)

The table `vault_items` is auto-created by Hibernate. Schema:

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| title | NVARCHAR(500) | Item title |
| content | NVARCHAR(MAX) | Full content |
| ai_context | NVARCHAR(MAX) | AI-generated summary |
| item_type | NVARCHAR(50) | URL, IMAGE, or TEXT |
| tags | NVARCHAR(500) | Auto-generated tags |
| source_url | NVARCHAR(1000) | Original URL (for URL type) |
| created_at | DATETIME2 | Creation timestamp |
| updated_at | DATETIME2 | Last update timestamp |

## 🎯 Features

- ✅ User pastes URL, uploads image, or types text
- ✅ App saves content to MS SQL database
- ✅ Auto-tags by type, platform, date
- ✅ Search and browse saved items
- 🔄 AI context generation (pending)

## 🛠️ Tech Stack

- **Backend**: Spring Boot 4.0.3, Spring Data JPA
- **Database**: MS SQL Server
- **Web UI**: Vaadin 25.0.7
- **Desktop UI**: JavaFX (to be implemented)
- **Java**: 21

