package ru.miet.activitycontroller.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class SettingsController
{
    @FXML private HBox switchThemeBox;
    @FXML private Label switchThemeLabel;
    @FXML private ToggleButton themeSwitch;
    @FXML private Button saveButton;

    private boolean initialDarkTheme;

    @FXML
    public void initialize()
    {
        initialDarkTheme = ThemeManager.IsDarkTheme();
        themeSwitch.setSelected(initialDarkTheme);
        updateSwitchVisuals(initialDarkTheme);
    }

    @FXML
    private void onThemeSwitchClick()
    {
        boolean isDark = themeSwitch.isSelected();
        updateSwitchVisuals(isDark);
        ThemeManager.setDarkTheme(isDark);
    }

    private void updateSwitchVisuals(boolean isDark)
    {
        if (isDark)
        {
            themeSwitch.setText("ВКЛ");
            themeSwitch.getStyleClass().setAll("toggle-button", "theme-switch-on");
        }
        else
        {
            themeSwitch.setText("ВЫКЛ");
            themeSwitch.getStyleClass().setAll("toggle-button", "theme-switch-off");
        }
    }

    @FXML
    private void onSaveSettingsClick()
    {
        boolean selectedDark = themeSwitch.isSelected();
        initialDarkTheme = selectedDark;
        ThemeManager.saveDarkTheme(selectedDark);
        closeWindow();
    }

    public void restoreIfCancelled()
    {
        if (ThemeManager.IsDarkTheme() != initialDarkTheme)
        {
            ThemeManager.setDarkTheme(initialDarkTheme);
        }
    }

    private void closeWindow()
    {
        Stage stage = (Stage) switchThemeBox.getScene().getWindow();
        stage.close();
    }
}
