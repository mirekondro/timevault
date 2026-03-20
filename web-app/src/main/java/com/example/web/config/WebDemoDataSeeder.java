package com.example.web.config;

import com.example.shared.security.PasswordHasher;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds the backend database with predictable demo users and non-image items.
 */
@Component
public class WebDemoDataSeeder {

    private static final String TYPE_URL = "URL";
    private static final String TYPE_TEXT = "TEXT";

    public void seed(Connection connection) throws SQLException {
        long asimUserId = insertUser(connection, "asim@gmail.com", "1234.asim", LocalDateTime.now().minusDays(3));
        long mirekUserId = insertUser(connection, "mirek@gmail.com", "1234.mirek", LocalDateTime.now().minusDays(2));

        insertItems(connection, asimUserId, buildAsimItems());
        insertItems(connection, mirekUserId, buildMirekItems());
    }

    private long insertUser(Connection connection,
                            String email,
                            String rawPassword,
                            LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO dbo.vault_users (email, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, PasswordHasher.hash(rawPassword));
            statement.setTimestamp(3, Timestamp.valueOf(createdAt));
            statement.setTimestamp(4, Timestamp.valueOf(createdAt));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        }

        throw new SQLException("Could not create demo user " + email + ".");
    }

    private void insertItems(Connection connection, long userId, List<DemoItem> items) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO dbo.vault_items
                    (user_id, title, content, ai_context, item_type, tags, source_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (DemoItem item : items) {
                statement.setLong(1, userId);
                statement.setString(2, item.title());
                statement.setString(3, item.content());
                statement.setString(4, item.aiContext());
                statement.setString(5, item.itemType());
                statement.setString(6, item.tags());
                statement.setString(7, item.sourceUrl());
                statement.setTimestamp(8, Timestamp.valueOf(item.createdAt()));
                statement.setTimestamp(9, Timestamp.valueOf(item.updatedAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<DemoItem> buildAsimItems() {
        LocalDateTime baseTime = LocalDateTime.now().minusDays(1).withMinute(15).withSecond(0).withNano(0);
        return List.of(
                urlItem(
                        "JavaFX responsive layout notes",
                        "Reference page for header/footer layout ideas and split-pane spacing.",
                        "Saved as a URL because Asim is comparing desktop layout patterns before polishing the main scene.",
                        "URL, JavaFX, Layout, UI",
                        "https://openjfx.io/javadoc/21/javafx.controls/javafx/scene/control/SplitPane.html",
                        baseTime.minusHours(10)),
                urlItem(
                        "Password hashing checklist",
                        "Security notes for account registration, salts, and safe password storage.",
                        "Saved as a URL because Asim is validating the login flow and wants a quick security refresher nearby.",
                        "URL, Security, Authentication, PBKDF2",
                        "https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html",
                        baseTime.minusHours(8)),
                textItem(
                        "Release planning notes",
                        "1. Finalize desktop auth flow.\n2. Review demo seed content.\n3. Test English and Danish switching.\n4. Record walkthrough screenshots for the project hand-in.",
                        "This note captures Asim's next actions for making the app feel polished and presentation-ready.",
                        "TEXT, Planning, Sprint, Notes",
                        baseTime.minusHours(6)),
                textItem(
                        "Feedback from UX review",
                        "The header search feels stronger now that filters live in the top bar. Next iteration should focus on the profile menu and icon polish.",
                        "This text entry preserves feedback from a design pass so the same ideas are easy to revisit later.",
                        "TEXT, Feedback, UX, Review",
                        baseTime.minusHours(4))
        );
    }

    private List<DemoItem> buildMirekItems() {
        LocalDateTime baseTime = LocalDateTime.now().minusHours(16).withMinute(45).withSecond(0).withNano(0);
        return List.of(
                urlItem(
                        "SQL Server indexing guide",
                        "Reference article about indexing user-owned tables and improving sorted list queries.",
                        "Saved as a URL because Mirek is keeping database performance references near the rest of the project material.",
                        "URL, Database, SQL Server, Indexing",
                        "https://learn.microsoft.com/sql/relational-databases/sql-server-index-design-guide",
                        baseTime.minusHours(12)),
                urlItem(
                        "Localization checklist",
                        "Helpful checklist for validating translation keys, locale switching, and date formatting.",
                        "Saved as a URL because Mirek is reviewing how to ship the first bilingual desktop build cleanly.",
                        "URL, i18n, Localization, QA",
                        "https://phrase.com/blog/posts/10-common-mistakes-in-software-localization/",
                        baseTime.minusHours(9)),
                textItem(
                        "Database migration reminders",
                        "Make sure seeded users use hashed passwords, legacy items keep an owner, and reset mode stays clearly documented in the example config.",
                        "This note keeps the database migration rules in one place so demo mode and real mode are easy to reason about.",
                        "TEXT, Database, Migration, Notes",
                        baseTime.minusHours(7)),
                textItem(
                        "Presentation script draft",
                        "Start with login and registration, then show separate account data, then switch to Danish, then explain how reset mode seeds a clean demo every launch.",
                        "This draft helps Mirek rehearse the story of the application from first launch to multi-language demo data.",
                        "TEXT, Demo, Presentation, Script",
                        baseTime.minusHours(5))
        );
    }

    private DemoItem urlItem(String title,
                             String content,
                             String aiContext,
                             String tags,
                             String sourceUrl,
                             LocalDateTime createdAt) {
        return new DemoItem(title, content, aiContext, TYPE_URL, tags, sourceUrl, createdAt, createdAt);
    }

    private DemoItem textItem(String title,
                              String content,
                              String aiContext,
                              String tags,
                              LocalDateTime createdAt) {
        return new DemoItem(title, content, aiContext, TYPE_TEXT, tags, null, createdAt, createdAt);
    }

    private record DemoItem(String title,
                            String content,
                            String aiContext,
                            String itemType,
                            String tags,
                            String sourceUrl,
                            LocalDateTime createdAt,
                            LocalDateTime updatedAt) {
    }
}
