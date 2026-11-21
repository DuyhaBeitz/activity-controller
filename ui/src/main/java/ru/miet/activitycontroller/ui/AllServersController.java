package ru.miet.activitycontroller.ui;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import ru.miet.activitycontroller.core.Database;
import ru.miet.activitycontroller.core.Database.ServerResult;
import ru.miet.activitycontroller.core.PingHistory;
import ru.miet.activitycontroller.core.ServerStatus;
import javafx.scene.layout.BorderPane;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.concurrent.Task;

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

    private boolean sortByResponseTime = false;
    @FXML private MenuItem sortByResponseItem;
    
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

        sortByResponseItem.setOnAction(e -> {
            sortByResponseTime = true;
            loadAndSortByResponseTime();
        });
        
        updateList(mapController.getAllServers());
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

    private void loadAndSortByResponseTime() {
        Task<List<ServerResult>> task = new Task<>()
        {
            @Override
            protected List<ServerResult> call() throws Exception
            {
                return PingHistory.GetSortedServersByAvgResponseTime(mapController.getDatabase(), 10).reversed();
            }
        };

        task.setOnSucceeded(e -> {
            List<ServerResult> sorted = task.getValue();
            if (sorted != null)
            {
                reorderUi(sorted);
            }
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            Viewer.showErrorAlert("Ошибка", "Не удалось отсортировать: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void reorderUi(List<ServerResult> sortedServers)
    {
        serversContainer.getChildren().clear();
        for (ServerResult server : sortedServers)
        {
            ServerRowView rowView = serverRowsById.get(server.id());
            if (rowView != null)
            {
                serversContainer.getChildren().add(rowView.container);
            }
        }
    }

    private void createServerRow(ServerResult server)
    {
        HBox serverRow = new HBox(8);
        serverRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        serverRow.getStyleClass().add("server-row");
        serverRow.setMaxWidth(Double.MAX_VALUE);

        int radius = (int) LayoutScale.scaled(6);
        Circle statusIndicator = new Circle(radius);
        updateCircleColor(statusIndicator, server.status());

        Label nameLabel = new Label(server.name());
        nameLabel.getStyleClass().add("server-row-name");
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
        circle.getStyleClass().setAll("status-dot", switch (status)
        {
            case OK -> "status-ok";
            case WARNING -> "status-warning";
            case ERROR -> "status-error";
            case CONTROL -> "status-control";
            default -> "status-unknown";
        });
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
