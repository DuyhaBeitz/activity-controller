package ru.miet.activitycontroller.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.miet.activitycontroller.core.Database;

public class AddServiceController
{
    @FXML 
    private TextField nameField;

    @FXML 
    private TextField latField;

    @FXML 
    private TextField lngField;

    @FXML
    private ComboBox<String> statusComboBox;

    private Database database;
    private MapController mapController;

    @FXML
    public void initialize()
    {
        statusComboBox.setItems(FXCollections.observableArrayList("OK", "WARNING", "ERROR", "CONTROL"));
        statusComboBox.setValue("OK");
    }

    public void setDependencies(Database database, MapController mapController)
    {
        this.database = database;
        this.mapController = mapController;
    }

    @FXML 
    private void onSaveClick()
    {
        try
        {
            String name = nameField.getText().trim();
            float lat = Float.parseFloat(latField.getText().trim());
            float lng = Float.parseFloat(lngField.getText().trim());
            String status = statusComboBox.getValue();

            if (name.isEmpty())
            {
                throw new IllegalArgumentException("Название не может быть пустым!");
            }

            int generatedId = database.insertServer(name, lat, lng);

            if (generatedId != -1)
            {
                mapController.updateServiceStatusInUI(generatedId, status);
                mapController.addNewMarkerDirectly(generatedId, lat, lng, name, status);
            }

            Stage stage = (Stage) nameField.getScene().getWindow();
            stage.close();
        }
        catch (NumberFormatException ex)
        {
            showErrorAlert("Ошибка ввода", "Координаты должны быть числами (разделитель — точка).");
        }
        catch (Exception ex)
        {
            showErrorAlert("Ошибка", ex.getMessage());
        } 
    }

    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
