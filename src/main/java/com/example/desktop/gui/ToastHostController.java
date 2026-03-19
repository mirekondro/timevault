package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.ToastNotification;
import com.example.desktop.model.ToastNotificationType;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.HostServices;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared floating toast overlay used by both desktop scenes.
 */
public class ToastHostController implements AppContextAware {

    private static final Duration ENTER_DURATION = Duration.millis(220);
    private static final Duration EXIT_DURATION = Duration.millis(260);

    @FXML
    private StackPane rootPane;

    @FXML
    private VBox toastContainer;

    private final Map<Long, StackPane> visibleToasts = new LinkedHashMap<>();
    private final Map<Long, PauseTransition> dismissalTimers = new HashMap<>();
    private final ListChangeListener<ToastNotification> notificationsListener = change -> {
        while (change.next()) {
            if (change.wasAdded() && isActiveHost()) {
                change.getAddedSubList().forEach(this::showToast);
            }
            if (change.wasRemoved()) {
                change.getRemoved().forEach(this::hideToast);
            }
        }
    };

    private AppModel appModel;
    private Scene trackedScene;
    private Window trackedWindow;
    private boolean useAppNotifications = true;
    private long nextLocalToastId = -1;
    private final ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> handleSceneChanged(oldScene, newScene);
    private final ChangeListener<Window> windowListener = (obs, oldWindow, newWindow) -> handleWindowChanged(oldWindow, newWindow);
    private final ChangeListener<Boolean> showingListener = (obs, wasShowing, isShowing) -> {
        clearToasts();
        if (Boolean.TRUE.equals(isShowing)) {
            syncToasts();
        }
    };

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;
        if (useAppNotifications) {
            appModel.getNotifications().addListener(notificationsListener);
            rootPane.sceneProperty().addListener(sceneListener);

            if (rootPane.getScene() != null) {
                handleSceneChanged(null, rootPane.getScene());
            }
        }
    }

    public void setTopOffset(double topOffset) {
        rootPane.setPadding(new Insets(topOffset, 0, 0, 0));
    }

    public void setUseAppNotifications(boolean useAppNotifications) {
        if (this.useAppNotifications == useAppNotifications) {
            return;
        }

        this.useAppNotifications = useAppNotifications;
        clearToasts();
        if (appModel == null) {
            return;
        }

        if (useAppNotifications) {
            appModel.getNotifications().addListener(notificationsListener);
            rootPane.sceneProperty().addListener(sceneListener);
            if (rootPane.getScene() != null) {
                handleSceneChanged(null, rootPane.getScene());
            }
        } else {
            appModel.getNotifications().removeListener(notificationsListener);
            rootPane.sceneProperty().removeListener(sceneListener);
            if (trackedScene != null) {
                trackedScene.windowProperty().removeListener(windowListener);
            }
            if (trackedWindow != null) {
                trackedWindow.showingProperty().removeListener(showingListener);
            }
            trackedScene = null;
            trackedWindow = null;
        }
    }

    public void showLocalToast(String message, ToastNotificationType type) {
        if (message == null || message.isBlank()) {
            return;
        }

        ToastNotification notification = new ToastNotification(nextLocalToastId--, message, type);
        showToast(notification);

        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        dismissalTimers.put(notification.id(), delay);
        delay.setOnFinished(event -> hideToast(notification));
        delay.play();
    }

    private void handleSceneChanged(Scene oldScene, Scene newScene) {
        clearToasts();
        if (trackedScene != null) {
            trackedScene.windowProperty().removeListener(windowListener);
        }

        trackedScene = newScene;
        if (trackedScene == null) {
            handleWindowChanged(trackedWindow, null);
            return;
        }

        trackedScene.windowProperty().addListener(windowListener);
        handleWindowChanged(trackedWindow, trackedScene.getWindow());
    }

    private void handleWindowChanged(Window oldWindow, Window newWindow) {
        if (trackedWindow != null) {
            trackedWindow.showingProperty().removeListener(showingListener);
        }

        trackedWindow = newWindow;
        clearToasts();
        if (trackedWindow == null) {
            return;
        }

        trackedWindow.showingProperty().addListener(showingListener);
        if (trackedWindow.isShowing()) {
            syncToasts();
        }
    }

    private void syncToasts() {
        if (!useAppNotifications || appModel == null || !isActiveHost()) {
            return;
        }
        for (ToastNotification notification : appModel.getNotifications()) {
            showToast(notification);
        }
    }

    private boolean isActiveHost() {
        return rootPane.getScene() != null
                && rootPane.getScene().getWindow() != null
                && rootPane.getScene().getWindow().isShowing();
    }

    private void showToast(ToastNotification notification) {
        if (visibleToasts.containsKey(notification.id())) {
            return;
        }

        Label messageLabel = new Label(notification.message());
        messageLabel.getStyleClass().add("toast-message");
        messageLabel.setWrapText(true);

        StackPane toastCard = new StackPane(messageLabel);
        toastCard.getStyleClass().addAll("toast-card", styleClassFor(notification.type()));
        toastCard.setOpacity(0);
        toastCard.setTranslateY(-26);
        toastCard.setMaxWidth(460);
        toastCard.setPrefWidth(460);

        visibleToasts.put(notification.id(), toastCard);
        toastContainer.getChildren().add(toastCard);

        FadeTransition fadeIn = new FadeTransition(ENTER_DURATION, toastCard);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        TranslateTransition slideIn = new TranslateTransition(ENTER_DURATION, toastCard);
        slideIn.setFromY(-26);
        slideIn.setToY(0);

        new ParallelTransition(fadeIn, slideIn).play();
    }

    private void hideToast(ToastNotification notification) {
        PauseTransition dismissalTimer = dismissalTimers.remove(notification.id());
        if (dismissalTimer != null) {
            dismissalTimer.stop();
        }

        StackPane toastCard = visibleToasts.remove(notification.id());
        if (toastCard == null) {
            return;
        }

        FadeTransition fadeOut = new FadeTransition(EXIT_DURATION, toastCard);
        fadeOut.setFromValue(toastCard.getOpacity());
        fadeOut.setToValue(0);

        TranslateTransition slideOut = new TranslateTransition(EXIT_DURATION, toastCard);
        slideOut.setFromY(toastCard.getTranslateY());
        slideOut.setToY(-22);

        ParallelTransition exit = new ParallelTransition(fadeOut, slideOut);
        exit.setOnFinished(event -> toastContainer.getChildren().remove(toastCard));
        exit.play();
    }

    private String styleClassFor(ToastNotificationType type) {
        return switch (type) {
            case SUCCESS -> "toast-success";
            case ERROR -> "toast-error";
            case INFO -> "toast-info";
        };
    }

    private void clearToasts() {
        dismissalTimers.values().forEach(PauseTransition::stop);
        dismissalTimers.clear();
        visibleToasts.clear();
        toastContainer.getChildren().clear();
    }
}
