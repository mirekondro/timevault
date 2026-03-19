package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.stage.Stage;

/**
 * Shared desktop dialog context for item editors.
 */
public abstract class BaseItemDialogController implements AppContextAware {

    protected AppModel appModel;
    protected VaultManager vaultManager;
    protected Stage dialogStage;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;
        this.dialogStage = stage;
    }

    protected void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    protected void bindWindowTitle(String key) {
        if (dialogStage == null) {
            return;
        }
        dialogStage.titleProperty().unbind();
        dialogStage.titleProperty().bind(appModel.textBinding(key));
    }
}
