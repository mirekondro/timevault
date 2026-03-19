package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.stage.Stage;

/**
 * Shared initialization contract for desktop controllers.
 */
public interface AppContextAware {

    void setContext(AppModel appModel,
                    VaultManager vaultManager,
                    HostServices hostServices,
                    Stage stage,
                    DesktopNavigator navigator);
}
