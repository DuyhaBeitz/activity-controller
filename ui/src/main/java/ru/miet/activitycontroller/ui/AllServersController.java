package ru.miet.activitycontroller.ui;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import ru.miet.activitycontroller.core.Database;
import ru.miet.activitycontroller.core.Database.ServerResult;
import ru.miet.activitycontroller.core.ServerStatus;
import javafx.scene.layout.BorderPane;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllServersController
{
    @FXML private BorderPane rootPane;
    @FXML private Button backButton;
    @FXML private Button addButton;
    @FXML private VBox serversContainer;
    
    @FXML private VBox detailsPanel;
    @FXML private Label detailsTitle;
    @FXML private Label detailsStatus;
    @FXML private VBox notesBox;
    @FXML private TextArea detailsNotes;

    private MapController mapController;
    
    private final Map<Integer, ServerRowView> serverRowsById = new HashMap<>();
    private Integer selectedServerId = null;

    public Parent getRoot()
    {
        return rootPane;
    }

    public void setDependencies(MapController mapController)
    {
        this.mapController = mapController;
        
        // Подписываемся на кнопки
        backButton.setOnAction(e -> mapController.showMapView());
        addButton.setOnAction(e -> mapController.onAddServiceButton());
        
        applyStyles();
        // Первичная загрузка данных
        updateList(mapController.getAllServers());
    }

    private void applyStyles()
    {
        double buttonFontSize = LayoutScale.scaled(16);
        double buttonPadding = LayoutScale.scaled(8);
        
        backButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: " + buttonFontSize + "px; -fx-font-weight: bold; -fx-padding: " + buttonPadding + "px; -fx-cursor: hand;");
        addButton.setStyle("-fx-background-color: #3652a1; -fx-text-fill: white; -fx-font-size: " + buttonFontSize + "px; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-padding: " + buttonPadding + "px; -fx-cursor: hand;");
    }

    public void updateList(List<ServerResult> freshServers)
    {
        Map<Integer, ServerResult> freshById = new HashMap<>();
        for (ServerResult s : freshServers) freshById.put(s.id(), s);

        // 1. Удаление
        serverRowsById.entrySet().removeIf(entry -> {
            if (!freshById.containsKey(entry.getKey()))
            {
                serversContainer.getChildren().remove(entry.getValue().container);
                return true;
            }
            return false;
        });

        // 2. Обновление / Добавление
        for (ServerResult fresh : freshServers)
        {
            ServerRowView rowView = serverRowsById.get(fresh.id());
            if (rowView == null)
            {
                createServerRow(fresh);
            }
            else if (rowView.currentStatus != fresh.status())
            {
                updateCircleColor(rowView.statusIndicator, fresh.status());
                rowView.currentStatus = fresh.status();
            }
        }

        // 3. Обновление деталей, если выбран сервер
        if (selectedServerId != null)
        {
            ServerResult selected = freshById.get(selectedServerId);
            if (selected != null)
            {
                showServerDetails(selected);
            }
            else
            {
                hideDetailsPanel();
            }
        }
    }

    private void createServerRow(ServerResult server)
    {
        // Создаем HBox, кружок, лейбл (без кнопки)
        // ...
        // serverRow.setOnMouseClicked(e -> { selectedServerId = server.id(); showServerDetails(server); });
        // serverRowsById.put(server.id(), new ServerRowView(...));
    }

    private void showServerDetails(ServerResult server)
    {
        detailsPanel.setVisible(true);
        detailsPanel.setManaged(true);
        detailsTitle.setText("Сервер: " + server.name());
        // detailsStatus.setText("Текущий статус: " + getStatusText(server.status()));
        
        // if (server.notes() != null && !server.notes().trim().isEmpty())
        // {
        //     notesBox.setVisible(true);
        //     notesBox.setManaged(true);
        //     detailsNotes.setText(server.notes());
        // } else {
        //     notesBox.setVisible(false);
        //     notesBox.setManaged(false);
        // }
    }

    private void hideDetailsPanel()
    {
        selectedServerId = null;
        detailsPanel.setVisible(false);
        detailsPanel.setManaged(false);
    }

    private void updateCircleColor(Circle circle, ServerStatus status)
    {
        String color = switch (status)
        {
            case OK -> "#2ecc71";
            case WARNING -> "#f1c40f";
            case ERROR -> "#e74c3c";
            case CONTROL -> "#e67e22";
            default -> "#95a5a6";
        };
        circle.setStyle("-fx-fill: " + color + "; -fx-stroke: white; -fx-stroke-width: 1;");
    }

    private static String statusText(ServerStatus status)
    {
        return switch (status)
        {
            case OK -> "Все работает исправно";
            case ERROR -> "Сервис работает некорректно. Выполните диагностику и обратитесь к системному администратору";
            case CONTROL -> "У сервиса замечены проблемы. Уже ведутся работы по их исправлению";
            case WARNING -> "Время ответа сервиса растет. Рекомендуется провести диагностику и следить за его дальнейшей работой";
        };
    }
}
