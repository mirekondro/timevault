package com.example.web.views;

import com.example.shared.model.UserSession;
import com.example.shared.model.VaultItem;
import com.example.shared.service.AuthService;
import com.example.shared.service.VaultItemService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * WEB VERSION - Main View
 *
 * This view now supports account registration/login and only shows items that
 * belong to the authenticated user.
 */
@PageTitle("TimeVault - Your Digital Memory")
@Route("")
public class MainView extends VerticalLayout {

    private final VaultItemService vaultItemService;
    private final AuthService authService;

    private UserSession currentUser;
    private VerticalLayout itemsList;
    private H3 recentSectionTitle;

    private TextField searchField;
    private Button browseButton;
    private Button refreshButton;
    private Button saveUrlButton;
    private Button saveTextButton;
    private Upload imageUpload;
    private VerticalLayout authFormsLayout;
    private VerticalLayout authSessionLayout;
    private Span authSummary;
    private Paragraph authMeta;
    private EmailField loginEmailField;
    private PasswordField loginPasswordField;
    private EmailField registerEmailField;
    private PasswordField registerPasswordField;
    private PasswordField registerConfirmPasswordField;

    @Autowired
    public MainView(VaultItemService vaultItemService, AuthService authService) {
        this.vaultItemService = vaultItemService;
        this.authService = authService;
        this.currentUser = VaadinSession.getCurrent().getAttribute(UserSession.class);

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("main-view");

        add(createHeader());
        add(createAuthSection());
        add(createHeroSection());
        add(createContentSection());
        add(createRecentItemsSection());

        refreshAuthState();
        refreshRecentItems();
    }

    private Component createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.addClassName("header");
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        HorizontalLayout logo = new HorizontalLayout();
        logo.setAlignItems(FlexComponent.Alignment.CENTER);
        logo.setSpacing(true);
        Icon vaultIcon = VaadinIcon.ARCHIVES.create();
        vaultIcon.addClassName("logo-icon");
        H1 title = new H1("TimeVault");
        title.addClassName("logo-text");
        logo.add(vaultIcon, title);

        searchField = new TextField();
        searchField.setPlaceholder("Log in to search your vault");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addClassName("header-search");
        searchField.setWidth("400px");
        searchField.addValueChangeListener(event -> {
            if (currentUser == null) {
                return;
            }

            try {
                if (!event.getValue().isBlank()) {
                    updateItemsList(vaultItemService.search(currentUser.id(), event.getValue()), "No results found");
                    recentSectionTitle.setText("Search Results");
                } else {
                    loadRecentItems();
                }
            } catch (Exception exception) {
                showError("Error: " + exception.getMessage());
            }
        });

        browseButton = new Button("Browse My Vault", VaadinIcon.GRID.create());
        browseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        browseButton.addClassName("header-button");
        browseButton.addClickListener(event -> loadAllItems());

        HorizontalLayout actions = new HorizontalLayout(searchField, browseButton);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        header.add(logo, actions);
        return header;
    }

    private Component createAuthSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setMaxWidth("900px");
        section.setAlignSelf(Alignment.CENTER);
        section.addClassName("auth-section");

        H3 title = new H3("Account");
        title.addClassName("section-title");

        Paragraph subtitle = new Paragraph(
                "Register with email and password, then every upload and note is stored under your own user id."
        );
        subtitle.addClassName("panel-description");

        authFormsLayout = new VerticalLayout();
        authFormsLayout.setWidthFull();
        authFormsLayout.setPadding(false);
        authFormsLayout.setSpacing(false);
        authFormsLayout.addClassName("auth-card");

        Tab loginTab = new Tab("Log In");
        Tab registerTab = new Tab("Register");
        Tabs authTabs = new Tabs(loginTab, registerTab);
        authTabs.addClassName("input-tabs");
        authTabs.setWidthFull();

        VerticalLayout loginPanel = createLoginPanel();
        VerticalLayout registerPanel = createRegisterPanel();
        registerPanel.setVisible(false);

        authTabs.addSelectedChangeListener(event -> {
            boolean loginSelected = authTabs.getSelectedTab() == loginTab;
            loginPanel.setVisible(loginSelected);
            registerPanel.setVisible(!loginSelected);
        });

        authFormsLayout.add(authTabs, loginPanel, registerPanel);

        authSessionLayout = new VerticalLayout();
        authSessionLayout.setWidthFull();
        authSessionLayout.addClassName("auth-card");
        authSessionLayout.setSpacing(true);
        authSessionLayout.setVisible(false);

        authSummary = new Span("Not signed in");
        authSummary.addClassName("auth-summary");

        authMeta = new Paragraph("Create an account to start building your personal vault.");
        authMeta.addClassName("auth-meta");

        Button logoutButton = new Button("Log Out", VaadinIcon.SIGN_OUT.create());
        logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logoutButton.addClickListener(event -> handleLogout());

        authSessionLayout.add(authSummary, authMeta, logoutButton);

        section.add(title, subtitle, authFormsLayout, authSessionLayout);
        return section;
    }

    private VerticalLayout createLoginPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("auth-panel");

        loginEmailField = new EmailField("Email");
        loginEmailField.setPlaceholder("you@example.com");
        loginEmailField.setWidthFull();

        loginPasswordField = new PasswordField("Password");
        loginPasswordField.setPlaceholder("Your password");
        loginPasswordField.setWidthFull();

        Button loginButton = new Button("Log In", VaadinIcon.SIGN_IN.create());
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.addClickListener(event -> handleLogin());

        panel.add(loginEmailField, loginPasswordField, loginButton);
        return panel;
    }

    private VerticalLayout createRegisterPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("auth-panel");

        registerEmailField = new EmailField("Email");
        registerEmailField.setPlaceholder("you@example.com");
        registerEmailField.setWidthFull();

        registerPasswordField = new PasswordField("Password");
        registerPasswordField.setPlaceholder("At least 8 characters");
        registerPasswordField.setWidthFull();

        registerConfirmPasswordField = new PasswordField("Confirm Password");
        registerConfirmPasswordField.setPlaceholder("Repeat your password");
        registerConfirmPasswordField.setWidthFull();

        Button registerButton = new Button("Create Account", VaadinIcon.USER.create());
        registerButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        registerButton.addClickListener(event -> handleRegister());

        panel.add(registerEmailField, registerPasswordField, registerConfirmPasswordField, registerButton);
        return panel;
    }

    private Component createHeroSection() {
        VerticalLayout hero = new VerticalLayout();
        hero.setWidthFull();
        hero.setPadding(true);
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.addClassName("hero-section");

        H2 heroTitle = new H2("Save Everything, Keep It Yours");
        heroTitle.addClassName("hero-title");

        Paragraph heroSubtitle = new Paragraph(
                "Each URL, image, and note is stored with account ownership so your vault stays tied to your login."
        );
        heroSubtitle.addClassName("hero-subtitle");

        hero.add(heroTitle, heroSubtitle);
        return hero;
    }

    private Component createContentSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setMaxWidth("900px");
        section.addClassName("content-section");
        section.setAlignSelf(Alignment.CENTER);

        Tab urlTab = new Tab(VaadinIcon.LINK.create(), new Span("URL"));
        Tab imageTab = new Tab(VaadinIcon.PICTURE.create(), new Span("Image"));
        Tab textTab = new Tab(VaadinIcon.TEXT_LABEL.create(), new Span("Text"));

        Tabs tabs = new Tabs(urlTab, imageTab, textTab);
        tabs.setWidthFull();
        tabs.addClassName("input-tabs");

        VerticalLayout urlPanel = createUrlPanel();
        VerticalLayout imagePanel = createImagePanel();
        VerticalLayout textPanel = createTextPanel();

        imagePanel.setVisible(false);
        textPanel.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            urlPanel.setVisible(tabs.getSelectedTab() == urlTab);
            imagePanel.setVisible(tabs.getSelectedTab() == imageTab);
            textPanel.setVisible(tabs.getSelectedTab() == textTab);
        });

        section.add(tabs, urlPanel, imagePanel, textPanel);
        return section;
    }

    private VerticalLayout createUrlPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("input-panel");

        Paragraph description = new Paragraph(
                "Paste any URL and save it under your account. Only you will see the entry tied to your user id."
        );
        description.addClassName("panel-description");

        TextField urlField = new TextField();
        urlField.setPlaceholder("https://example.com/article");
        urlField.setPrefixComponent(VaadinIcon.LINK.create());
        urlField.setWidthFull();
        urlField.addClassName("url-input");

        saveUrlButton = new Button("Save to Vault", VaadinIcon.DOWNLOAD.create());
        saveUrlButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveUrlButton.addClassName("save-button");
        saveUrlButton.addClickListener(event -> {
            UserSession user = requireAuthenticatedUser("Log in before saving a URL.");
            if (user == null) {
                return;
            }

            if (urlField.getValue().isBlank()) {
                showError("Please enter a URL.");
                return;
            }

            try {
                String url = urlField.getValue();
                String title = "URL: " + url.substring(0, Math.min(url.length(), 50));
                String aiContext = "Saved URL content. AI context will be generated automatically.";
                vaultItemService.saveUrl(user.id(), url, title, url, aiContext);

                showSuccess("Saved URL to account #" + user.id() + ".");
                urlField.clear();
                refreshRecentItems();
            } catch (Exception exception) {
                showError("Error: " + exception.getMessage());
            }
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
                createFeatureItem(VaadinIcon.USER, "Saved under your user id"),
                createFeatureItem(VaadinIcon.TAG, "Auto-tagged by type, platform, and date"),
                createFeatureItem(VaadinIcon.DOWNLOAD, "Stored with account ownership")
        );

        panel.add(description, urlField, saveUrlButton, features);
        return panel;
    }

    private VerticalLayout createImagePanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("input-panel");

        Paragraph description = new Paragraph(
                "Upload images, screenshots, or photos. The saved record is linked back to your account."
        );
        description.addClassName("panel-description");

        imageUpload = new Upload();
        imageUpload.setWidthFull();
        imageUpload.setAcceptedFileTypes("image/*");
        imageUpload.setDropLabel(new Span("Drop image here or click to browse"));
        imageUpload.addClassName("image-upload");

        imageUpload.addSucceededListener(event -> {
            UserSession user = requireAuthenticatedUser("Log in before uploading an image.");
            if (user == null) {
                return;
            }

            try {
                String title = "Image: " + event.getFileName();
                String aiContext = "Image saved. AI analysis pending.";
                vaultItemService.saveImage(user.id(), title, event.getFileName(), aiContext);

                showSuccess("Saved image to account #" + user.id() + ".");
                refreshRecentItems();
            } catch (Exception exception) {
                showError("Error: " + exception.getMessage());
            }
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
                createFeatureItem(VaadinIcon.USER, "Account ownership tracked"),
                createFeatureItem(VaadinIcon.TAG, "Smart auto-tagging"),
                createFeatureItem(VaadinIcon.CALENDAR, "Date and metadata preserved")
        );

        panel.add(description, imageUpload, features);
        return panel;
    }

    private VerticalLayout createTextPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("input-panel");

        Paragraph description = new Paragraph(
                "Save notes, quotes, or ideas. Every text capture remains scoped to the signed-in account."
        );
        description.addClassName("panel-description");

        TextArea textArea = new TextArea();
        textArea.setPlaceholder("Type or paste your text here...");
        textArea.setWidthFull();
        textArea.setMinHeight("150px");
        textArea.addClassName("text-input");

        saveTextButton = new Button("Save to Vault", VaadinIcon.DOWNLOAD.create());
        saveTextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveTextButton.addClassName("save-button");
        saveTextButton.addClickListener(event -> {
            UserSession user = requireAuthenticatedUser("Log in before saving text.");
            if (user == null) {
                return;
            }

            if (textArea.getValue().isBlank()) {
                showError("Please enter some text.");
                return;
            }

            try {
                String content = textArea.getValue();
                String title = "Note: " + content.substring(0, Math.min(content.length(), 50));
                String aiContext = "Text note saved. AI context generated.";
                vaultItemService.saveText(user.id(), title, content, aiContext);

                showSuccess("Saved text to account #" + user.id() + ".");
                textArea.clear();
                refreshRecentItems();
            } catch (Exception exception) {
                showError("Error: " + exception.getMessage());
            }
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
                createFeatureItem(VaadinIcon.USER, "Owned by your account"),
                createFeatureItem(VaadinIcon.TAG, "Automatic categorization"),
                createFeatureItem(VaadinIcon.CLOCK, "Timestamped and searchable")
        );

        panel.add(description, textArea, saveTextButton, features);
        return panel;
    }

    private HorizontalLayout createFeatureItem(VaadinIcon iconName, String text) {
        HorizontalLayout item = new HorizontalLayout();
        item.setAlignItems(FlexComponent.Alignment.CENTER);
        item.addClassName("feature-item");

        Icon icon = iconName.create();
        icon.setSize("16px");
        icon.addClassName("feature-icon");

        Span label = new Span(text);
        label.addClassName("feature-text");

        item.add(icon, label);
        return item;
    }

    private Component createRecentItemsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setMaxWidth("900px");
        section.addClassName("recent-section");
        section.setAlignSelf(Alignment.CENTER);

        HorizontalLayout sectionHeader = new HorizontalLayout();
        sectionHeader.setWidthFull();
        sectionHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        sectionHeader.setAlignItems(FlexComponent.Alignment.CENTER);

        recentSectionTitle = new H3("Recent Saves");
        recentSectionTitle.addClassName("section-title");

        refreshButton = new Button("Refresh", VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        refreshButton.addClickListener(event -> refreshRecentItems());

        sectionHeader.add(recentSectionTitle, refreshButton);

        itemsList = new VerticalLayout();
        itemsList.setWidthFull();
        itemsList.setPadding(false);
        itemsList.setSpacing(true);
        itemsList.addClassName("items-list");

        section.add(sectionHeader, itemsList);
        return section;
    }

    private void handleLogin() {
        try {
            currentUser = authService.authenticate(loginEmailField.getValue(), loginPasswordField.getValue());
            VaadinSession.getCurrent().setAttribute(UserSession.class, currentUser);
            loginPasswordField.clear();
            refreshAuthState();
            refreshRecentItems();
            showSuccess("Signed in as " + currentUser.email() + ".");
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private void handleRegister() {
        if (!registerPasswordField.getValue().equals(registerConfirmPasswordField.getValue())) {
            showError("Passwords do not match.");
            return;
        }

        try {
            currentUser = authService.register(registerEmailField.getValue(), registerPasswordField.getValue());
            VaadinSession.getCurrent().setAttribute(UserSession.class, currentUser);
            registerPasswordField.clear();
            registerConfirmPasswordField.clear();
            refreshAuthState();
            refreshRecentItems();
            showSuccess("Created account for " + currentUser.email() + ".");
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private void handleLogout() {
        currentUser = null;
        VaadinSession.getCurrent().setAttribute(UserSession.class, null);
        searchField.clear();
        refreshAuthState();
        refreshRecentItems();
        showSuccess("Signed out.");
    }

    private UserSession requireAuthenticatedUser(String message) {
        if (currentUser != null) {
            return currentUser;
        }
        showError(message);
        return null;
    }

    private void refreshAuthState() {
        boolean authenticated = currentUser != null;

        authFormsLayout.setVisible(!authenticated);
        authSessionLayout.setVisible(authenticated);

        if (authenticated) {
            authSummary.setText("Signed in as " + currentUser.email());
            authMeta.setText("Account #" + currentUser.id() + " owns every vault item you create in this session.");
            searchField.setPlaceholder("Search your vault...");
        } else {
            authSummary.setText("Not signed in");
            authMeta.setText("Create an account to start building your personal vault.");
            searchField.setPlaceholder("Log in to search your vault");
            loginEmailField.clear();
            loginPasswordField.clear();
            registerEmailField.clear();
            registerPasswordField.clear();
            registerConfirmPasswordField.clear();
        }

        searchField.setEnabled(authenticated);
        browseButton.setEnabled(authenticated);
        refreshButton.setEnabled(authenticated);
        saveUrlButton.setEnabled(authenticated);
        saveTextButton.setEnabled(authenticated);
        imageUpload.setEnabled(authenticated);
    }

    private void loadRecentItems() {
        recentSectionTitle.setText("Recent Saves");
        updateItemsList(vaultItemService.findRecent(currentUser.id()), "Your vault is empty.");
    }

    private void loadAllItems() {
        if (requireAuthenticatedUser("Log in before browsing your vault.") == null) {
            return;
        }
        try {
            recentSectionTitle.setText("All Saves");
            updateItemsList(vaultItemService.findAll(currentUser.id()), "Your vault is empty.");
        } catch (Exception exception) {
            showError("Error: " + exception.getMessage());
        }
    }

    private void refreshRecentItems() {
        itemsList.removeAll();

        if (currentUser == null) {
            recentSectionTitle.setText("Your Vault");
            itemsList.add(createLoginRequiredState());
            return;
        }

        try {
            loadRecentItems();
        } catch (Exception exception) {
            itemsList.add(createErrorState(exception.getMessage()));
        }
    }

    private void updateItemsList(List<VaultItem> items, String emptyMessage) {
        itemsList.removeAll();

        if (items.isEmpty()) {
            itemsList.add(createEmptyState(emptyMessage));
            return;
        }

        for (VaultItem item : items) {
            itemsList.add(createVaultItemCard(item));
        }
    }

    private Component createLoginRequiredState() {
        VerticalLayout empty = new VerticalLayout();
        empty.setAlignItems(FlexComponent.Alignment.CENTER);
        empty.setPadding(true);

        Icon icon = VaadinIcon.LOCK.create();
        icon.setSize("48px");
        icon.setColor("var(--lumo-contrast-30pct)");

        H4 title = new H4("Sign in to open your vault");
        Paragraph text = new Paragraph("Register or log in above to see and save items under your own account.");

        empty.add(icon, title, text);
        return empty;
    }

    private Component createEmptyState(String message) {
        VerticalLayout empty = new VerticalLayout();
        empty.setAlignItems(FlexComponent.Alignment.CENTER);
        empty.setPadding(true);

        Icon icon = VaadinIcon.INBOX.create();
        icon.setSize("48px");
        icon.setColor("var(--lumo-contrast-30pct)");

        H4 title = new H4("No items yet");
        Paragraph text = new Paragraph(message);

        empty.add(icon, title, text);
        return empty;
    }

    private Component createErrorState(String message) {
        VerticalLayout error = new VerticalLayout();
        error.setAlignItems(FlexComponent.Alignment.CENTER);
        error.setPadding(true);

        Icon icon = VaadinIcon.WARNING.create();
        icon.setSize("48px");
        icon.setColor("var(--lumo-error-color)");

        H4 title = new H4("Database Connection Error");
        Paragraph text = new Paragraph("Check your SQL Server connection. Error: " + message);

        error.add(icon, title, text);
        return error;
    }

    private Component createVaultItemCard(VaultItem vaultItem) {
        HorizontalLayout card = new HorizontalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.addClassName("vault-item");
        card.setAlignItems(FlexComponent.Alignment.START);

        VaadinIcon iconType = switch (vaultItem.getItemType()) {
            case "URL" -> VaadinIcon.GLOBE;
            case "IMAGE" -> VaadinIcon.PICTURE;
            case "TEXT" -> VaadinIcon.TEXT_LABEL;
            default -> VaadinIcon.FILE;
        };

        Div iconWrapper = new Div();
        iconWrapper.addClassName("item-icon-wrapper");
        Icon icon = iconType.create();
        icon.addClassName("item-icon");
        iconWrapper.add(icon);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("item-content");

        H4 itemTitle = new H4(vaultItem.getTitle());
        itemTitle.addClassName("item-title");

        String previewSource = vaultItem.getAiContext() != null && !vaultItem.getAiContext().isBlank()
                ? vaultItem.getAiContext()
                : (vaultItem.getContent() == null ? "No content" : vaultItem.getContent());
        String contextText = previewSource.length() > 150 ? previewSource.substring(0, 150) + "..." : previewSource;
        Paragraph itemContext = new Paragraph(contextText);
        itemContext.addClassName("item-context");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        String metaText = vaultItem.getItemType() + " | Account #"
                + (vaultItem.getOwner() == null ? "?" : vaultItem.getOwner().getId())
                + " | "
                + (vaultItem.getTags() != null ? vaultItem.getTags() : "No tags")
                + " | "
                + (vaultItem.getCreatedAt() != null ? vaultItem.getCreatedAt().format(formatter) : "");
        Span itemMeta = new Span(metaText);
        itemMeta.addClassName("item-meta");

        content.add(itemTitle, itemContext, itemMeta);

        Button deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR);
        deleteButton.addClassName("item-action");
        deleteButton.addClickListener(event -> {
            UserSession user = requireAuthenticatedUser("Log in before deleting items.");
            if (user == null) {
                return;
            }

            boolean deleted = vaultItemService.delete(user.id(), vaultItem.getId());
            if (deleted) {
                showSuccess("Deleted item #" + vaultItem.getId() + ".");
                refreshRecentItems();
            } else {
                showError("That item no longer belongs to your account.");
            }
        });

        card.add(iconWrapper, content, deleteButton);
        card.expand(content);
        return card;
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
