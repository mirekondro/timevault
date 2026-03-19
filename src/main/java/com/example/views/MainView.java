package com.example.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("TimeVault - Your Digital Memory")
@Route("")
public class MainView extends VerticalLayout {

    public MainView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("main-view");

        add(createHeader());
        add(createHeroSection());
        add(createContentSection());
        add(createRecentItemsSection());
    }

    private Component createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.addClassName("header");
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        // Logo and title
        HorizontalLayout logo = new HorizontalLayout();
        logo.setAlignItems(FlexComponent.Alignment.CENTER);
        logo.setSpacing(true);
        Icon vaultIcon = VaadinIcon.ARCHIVES.create();
        vaultIcon.addClassName("logo-icon");
        H1 title = new H1("TimeVault");
        title.addClassName("logo-text");
        logo.add(vaultIcon, title);

        // Search bar
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search your vault...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addClassName("header-search");
        searchField.setWidth("400px");

        // Action buttons
        Button browseBtn = new Button("Browse All", VaadinIcon.GRID.create());
        browseBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        browseBtn.addClassName("header-button");

        HorizontalLayout actions = new HorizontalLayout(searchField, browseBtn);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        header.add(logo, actions);
        return header;
    }

    private Component createHeroSection() {
        VerticalLayout hero = new VerticalLayout();
        hero.setWidthFull();
        hero.setPadding(true);
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.addClassName("hero-section");

        H2 heroTitle = new H2("Save Everything, Find Anything");
        heroTitle.addClassName("hero-title");

        Paragraph heroSubtitle = new Paragraph(
            "Your personal digital vault with AI-powered context and smart auto-tagging"
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

        // Tab selection
        Tab urlTab = new Tab(VaadinIcon.LINK.create(), new Span("URL"));
        Tab imageTab = new Tab(VaadinIcon.PICTURE.create(), new Span("Image"));
        Tab textTab = new Tab(VaadinIcon.TEXT_LABEL.create(), new Span("Text"));

        Tabs tabs = new Tabs(urlTab, imageTab, textTab);
        tabs.setWidthFull();
        tabs.addClassName("input-tabs");

        // Content panels
        VerticalLayout urlPanel = createUrlPanel();
        VerticalLayout imagePanel = createImagePanel();
        VerticalLayout textPanel = createTextPanel();

        // Show/hide panels based on selected tab
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
            "Paste any URL - articles, videos, tweets, or web pages. We'll fetch and save the full content."
        );
        description.addClassName("panel-description");

        TextField urlField = new TextField();
        urlField.setPlaceholder("https://example.com/article");
        urlField.setPrefixComponent(VaadinIcon.LINK.create());
        urlField.setWidthFull();
        urlField.addClassName("url-input");

        Button saveButton = new Button("Save to Vault", VaadinIcon.DOWNLOAD.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveButton.addClassName("save-button");
        saveButton.addClickListener(e -> {
            if (urlField.getValue().isEmpty()) {
                Notification.show("Please enter a URL", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                Notification notification = Notification.show(
                    "✓ Content saved! AI is generating context...",
                    3000,
                    Notification.Position.BOTTOM_END
                );
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                urlField.clear();
            }
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
            createFeatureItem(VaadinIcon.MAGIC, "AI-generated context in 3 sentences"),
            createFeatureItem(VaadinIcon.TAG, "Auto-tagged by type, platform & date"),
            createFeatureItem(VaadinIcon.DOWNLOAD, "Full content saved locally")
        );

        panel.add(description, urlField, saveButton, features);
        return panel;
    }

    private VerticalLayout createImagePanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("input-panel");

        Paragraph description = new Paragraph(
            "Upload images, screenshots, or photos. AI will analyze and tag them automatically."
        );
        description.addClassName("panel-description");

        Upload upload = new Upload();
        upload.setWidthFull();
        upload.setAcceptedFileTypes("image/*");
        upload.setDropLabel(new Span("Drop image here or click to browse"));
        upload.addClassName("image-upload");

        upload.addFinishedListener(event -> {
            Notification notification = Notification.show(
                "✓ Image saved! AI is analyzing content...",
                3000,
                Notification.Position.BOTTOM_END
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
            createFeatureItem(VaadinIcon.EYE, "AI visual analysis & context"),
            createFeatureItem(VaadinIcon.TAG, "Smart auto-tagging"),
            createFeatureItem(VaadinIcon.CALENDAR, "Date & metadata preserved")
        );

        panel.add(description, upload, features);
        return panel;
    }

    private VerticalLayout createTextPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("input-panel");

        Paragraph description = new Paragraph(
            "Save notes, quotes, thoughts, or any text. Perfect for quick captures."
        );
        description.addClassName("panel-description");

        TextArea textArea = new TextArea();
        textArea.setPlaceholder("Type or paste your text here...");
        textArea.setWidthFull();
        textArea.setMinHeight("150px");
        textArea.addClassName("text-input");

        Button saveButton = new Button("Save to Vault", VaadinIcon.DOWNLOAD.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveButton.addClassName("save-button");
        saveButton.addClickListener(e -> {
            if (textArea.getValue().isEmpty()) {
                Notification.show("Please enter some text", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                Notification notification = Notification.show(
                    "✓ Text saved! AI context generated.",
                    3000,
                    Notification.Position.BOTTOM_END
                );
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                textArea.clear();
            }
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
            createFeatureItem(VaadinIcon.LIGHTBULB, "AI context generation"),
            createFeatureItem(VaadinIcon.TAG, "Automatic categorization"),
            createFeatureItem(VaadinIcon.CLOCK, "Timestamped & searchable")
        );

        panel.add(description, textArea, saveButton, features);
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

        H3 sectionTitle = new H3("Recent Saves");
        sectionTitle.addClassName("section-title");

        Button viewAllBtn = new Button("View All", VaadinIcon.ARROW_RIGHT.create());
        viewAllBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        viewAllBtn.setIconAfterText(true);

        sectionHeader.add(sectionTitle, viewAllBtn);

        // Sample items (placeholder)
        VerticalLayout itemsList = new VerticalLayout();
        itemsList.setWidthFull();
        itemsList.setPadding(false);
        itemsList.setSpacing(true);
        itemsList.addClassName("items-list");

        itemsList.add(
            createSampleItem(
                VaadinIcon.GLOBE,
                "Article: The Future of AI",
                "An in-depth analysis of artificial intelligence trends and predictions for 2026. Covers machine learning, neural networks, and ethical considerations.",
                "Article • Medium • Today"
            ),
            createSampleItem(
                VaadinIcon.PICTURE,
                "Screenshot: Design Inspiration",
                "A beautiful dashboard design showcasing modern UI patterns. Features gradient backgrounds, glassmorphism effects, and intuitive navigation elements.",
                "Image • Screenshot • Yesterday"
            ),
            createSampleItem(
                VaadinIcon.TEXT_LABEL,
                "Note: Project Ideas",
                "Brainstorming session notes about potential side projects. Includes ideas for productivity tools, creative applications, and developer utilities.",
                "Text • Note • 2 days ago"
            )
        );

        section.add(sectionHeader, itemsList);
        return section;
    }

    private Component createSampleItem(VaadinIcon iconName, String title, String context, String meta) {
        HorizontalLayout item = new HorizontalLayout();
        item.setWidthFull();
        item.setPadding(true);
        item.addClassName("vault-item");
        item.setAlignItems(FlexComponent.Alignment.START);

        // Icon
        Div iconWrapper = new Div();
        iconWrapper.addClassName("item-icon-wrapper");
        Icon icon = iconName.create();
        icon.addClassName("item-icon");
        iconWrapper.add(icon);

        // Content
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("item-content");

        H4 itemTitle = new H4(title);
        itemTitle.addClassName("item-title");

        Paragraph itemContext = new Paragraph(context);
        itemContext.addClassName("item-context");

        Span itemMeta = new Span(meta);
        itemMeta.addClassName("item-meta");

        content.add(itemTitle, itemContext, itemMeta);

        // Actions
        Button openBtn = new Button(VaadinIcon.EXTERNAL_LINK.create());
        openBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        openBtn.addClassName("item-action");
        openBtn.getElement().setAttribute("aria-label", "Open item");

        item.add(iconWrapper, content, openBtn);
        item.expand(content);

        return item;
    }
}

