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

    @FXML
    private TextField addressField;


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

    private boolean isValidServerAdress(String address)
    {
        if (address == null || address.isEmpty())
        {
            return false;
        }

        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        String domainPattern = "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}$";

        if (address.matches(ipPattern) || address.matches(domainPattern))
        {
            return true;
        }

        try
        {
            java.net.URI uri = new java.net.URI(address);
            String host = uri.getHost();

            return host != null && (host.matches(ipPattern) || host.matches(domainPattern));
        }
        catch(java.net.URISyntaxException e)
        {
            return false;
        }
    }

    @FXML 
    private void onSaveClick()
    {
        try
        {
            String name = nameField.getText().trim();
            String address = addressField.getText().trim();
            float lat = Float.parseFloat(latField.getText().trim());
            float lng = Float.parseFloat(lngField.getText().trim());
            String status = "OK";

            if (name.isEmpty())
            {
                throw new IllegalArgumentException("Название не может быть пустым!");
            }

            if (!isValidServerAdress(address))
            {
                throw new IllegalArgumentException("Введен некорректный адрес");
            }

            if (lat < -90.0f || lat > 90.0f)
            {
                throw new IllegalArgumentException("Широта расположения должна находиться в диапазоне от -90 до 90!");
            }
            if (lng < -180.0f || lng > 180.0f)
            {
                throw new IllegalArgumentException("Долгота расположения должна находиться в диапазоне от -180 до 180!");
            }

            int generatedId = database.insertServer(name, lat, lng, ServerStatus.valueOf(status), address);

            if (generatedId != -1)
            {
                mapController.updateServiceStatusInUI(generatedId, status);
                mapController.addNewMarkerDirectly(generatedId, lat, lng, name, status, address);
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
