package ru.miet.activitycontroller.ui;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.miet.activitycontroller.core.Database;
import ru.miet.activitycontroller.core.ServerStatus;

public class AddServiceController
{
    @FXML 
    private TextField nameField;

    @FXML 
    private TextField latField;

    @FXML 
    private TextField lngField;


    private Database database;
    private MapController mapController;

    public void setDependencies(Database database, MapController mapController)
    {
        this.database = database;
        this.mapController = mapController;
    }

    @FXML
    private void onSelectOnMapClick()
    {
        try {
            if (mapController == null) {
                Viewer.showErrorAlert("Ошибка", "Связь с главным контроллером утеряна.");
                return;
            }

            Stage stage = (Stage) nameField.getScene().getWindow();
            stage.hide(); 

            mapController.getWebEngine().executeScript("enableMapClickSelection()");
        } catch (Exception ex) {
            Viewer.showErrorAlert("Ошибка", ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void populateCoordinates(double lat, double lng)
    {
        latField.setText(String.format(java.util.Locale.US, "%.5f", lat));
        lngField.setText(String.format(java.util.Locale.US, "%.5f", lng));

        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.show();
    }

    @FXML 
    private void onSaveClick()
    {
        try
        {
            String name = nameField.getText().trim();
            float lat = Float.parseFloat(latField.getText().trim());
            float lng = Float.parseFloat(lngField.getText().trim());
            String status = "OK";

            if (name.isEmpty())
            {
                throw new IllegalArgumentException("Название не может быть пустым!");
            }

            int generatedId = database.insertServer(name, lat, lng, ServerStatus.valueOf(status));

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
            Viewer.showErrorAlert("Ошибка ввода", "Координаты должны быть числами (разделитель — точка).");
        }
        catch (Exception ex)
        {
            Viewer.showErrorAlert("Ошибка", ex.getMessage());
        } 
    }
}
