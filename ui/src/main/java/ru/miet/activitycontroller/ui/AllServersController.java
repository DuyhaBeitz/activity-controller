package ru.miet.activitycontroller.ui;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
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
    @FXML private MenuButton sortButton;
    @FXML private MenuButton filterButton;
    @FXML private Label mainLabel;
    
    @FXML private ScrollPane detailsScrollPane;
    @FXML private VBox detailsPanel;
    @FXML private Label detailsTitle;
    @FXML private Label detailsStatus;
    @FXML private VBox notesBox;
    @FXML private Label notesLabel;
    @FXML private TextArea detailsNotes;

    private MapController mapController;
    
    private final Map<Integer, ServerRowView> serverRowsById = new HashMap<>();
    private final Map<Integer, ServerResult> currentServers = new HashMap<>();
    private Integer selectedServerId = null;

    public Parent getRoot()
    {
        return rootPane;
    }

    public void setDependencies(MapController mapController)
    {
        this.mapController = mapController;
        
        backButton.setOnAction(e -> mapController.showMapView());
        addButton.setOnAction(e -> mapController.onAddServiceFromList());
        
        applyStyles();
        updateList(mapController.getAllServers());
    }

    private void applyStyles()
    {
        double smallLabelFontSize = LayoutScale.scaled(18);
        double largeLabelFontSize = LayoutScale.scaled(28);
        double buttonFontSize = LayoutScale.scaled(16);
        double padding = LayoutScale.scaled(8);
        
        backButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: " + buttonFontSize + "px; -fx-font-weight: bold; -fx-padding: " + padding + "px; -fx-cursor: hand;");
        addButton.setStyle("-fx-background-color: #3652a1; -fx-text-fill: white; -fx-font-size: " + buttonFontSize + "px; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-padding: " + padding + "px; -fx-cursor: hand;");

        sortButton.setStyle("-fx-font-size: " + buttonFontSize + "px; -fx-background-radius: 4; -fx-padding: " + padding + "px;");
        filterButton.setStyle("-fx-font-size: " + buttonFontSize + "px; -fx-background-radius: 4; -fx-padding: " + padding + "px;");

        mainLabel.setStyle("-fx-font-size: " + largeLabelFontSize + "px; -fx-font-weight: bold; -fx-padding: " + padding + "px; -fx-text-fill: #232853;");


        detailsTitle.setStyle("-fx-font-size: " + largeLabelFontSize + "px; -fx-font-weight: bold; -fx-text-fill: #1c1c1c;");
        detailsStatus.setStyle("-fx-font-size: " + smallLabelFontSize + "px; -fx-text-fill: #1c1c1c;");

        notesLabel.setStyle("-fx-font-size: " + smallLabelFontSize + "px; -fx-font-weight: bold; -fx-text-fill: #1c1c1c;");
        detailsNotes.setStyle("-fx-font-size: " + smallLabelFontSize + "px; -fx-padding: " + padding + "px; -fx-control-inner-background: #f4f6f7; -fx-border-color: #dcdcdc;");
    }

    public void updateList(List<ServerResult> freshServers)
    {
        currentServers.clear();
        for (ServerResult s : freshServers) currentServers.put(s.id(), s);

        serverRowsById.entrySet().removeIf(entry -> {
            if (!currentServers.containsKey(entry.getKey()))
            {
                serversContainer.getChildren().remove(entry.getValue().container);
                return true;
            }
            return false;
        });

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
                
                // Если этот сервер сейчас открыт в правой панели — обновляем статус там
                if (selectedServerId != null && selectedServerId == fresh.id())
                {
                    showServerDetails(fresh);
                }
            }
        }
    }

    private void createServerRow(ServerResult server)
    {
        HBox serverRow = new HBox(8);
        serverRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        int padding = (int) LayoutScale.scaled(8);
        serverRow.setStyle(String.format("-fx-padding: %d; -fx-background-color: #e3e3e3; -fx-background-radius: 4; -fx-cursor: hand;", padding));
        serverRow.setMaxWidth(Double.MAX_VALUE);

        int radius = (int) LayoutScale.scaled(6);
        Circle statusIndicator = new Circle(radius);
        updateCircleColor(statusIndicator, server.status());

        Label nameLabel = new Label(server.name());
        double labelFontSize = LayoutScale.scaled(14);
        nameLabel.setStyle(String.format("-fx-text-fill: #1c1c1c; -fx-font-size: %.0fpx; -fx-font-weight: bold;", labelFontSize));
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        serverRow.getChildren().addAll(statusIndicator, nameLabel);

        serverRow.setOnMouseClicked(e -> {
            selectedServerId = server.id();
            ServerResult current = currentServers.getOrDefault(server.id(), server);
            showServerDetails(current);
        });

        serversContainer.getChildren().add(serverRow);

        ServerRowView view = new ServerRowView(serverRow, statusIndicator, nameLabel, null, server.status());
        serverRowsById.put(server.id(), view);
    }

    private void showServerDetails(ServerResult server)
    {
        detailsScrollPane.setVisible(true);
        detailsScrollPane.setManaged(true);
        detailsTitle.setText("Сервер: " + server.name());
        detailsStatus.setText("Текущий статус: " + getStatusText(server.status()));
        
        if (server.notes() != null && !server.notes().trim().isEmpty())
        {
            notesBox.setVisible(true);
            notesBox.setManaged(true);
            detailsNotes.setText(server.notes());
        }
        else
        {
            notesBox.setVisible(false);
            notesBox.setManaged(false);
        }
    }

    private void hideDetailsPanel()
    {
        selectedServerId = null;
        detailsScrollPane.setVisible(false);
        detailsScrollPane.setManaged(false);
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

    private static String getStatusText(ServerStatus status)
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
