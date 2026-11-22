package ru.miet.activitycontroller.ui;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import ru.miet.activitycontroller.core.Database.ServerResult;
import ru.miet.activitycontroller.core.PingHistory;
import ru.miet.activitycontroller.core.ServerStatus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllServersController
{
    @FXML private BorderPane rootPane;
    @FXML private Button backButton;
    @FXML private Button addButton;
    @FXML private TextField searchField;
    @FXML private VBox serversContainer;
    @FXML private MenuButton sortButton;
    @FXML private MenuButton filterButton;
    @FXML private Label mainLabel;

    @FXML private MenuItem sortByResponseItem;

    @FXML private ScrollPane detailsScrollPane;
    @FXML private VBox detailsPanel;
    @FXML private Label detailsTitle;
    @FXML private Label detailsStatus;
    @FXML private VBox notesBox;
    @FXML private Label notesLabel;
    @FXML private TextArea detailsNotes;

    private MapController mapController;

    private FilteredList<ServerResult> filteredServers;
    private SortedList<ServerResult> sortedServers;

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

        ObservableList<ServerResult> sharedList = mapController.getMasterServers();
        this.filteredServers = new FilteredList<>(sharedList, p -> true);
        this.sortedServers = new SortedList<>(filteredServers);

        sortedServers.addListener((ListChangeListener<ServerResult>) change -> {
            syncRowsWithSortedList();
        });

        searchField.textProperty().addListener((obs, oldText, newText) -> {
            updateFilterPredicate(newText);
        });

        backButton.setOnAction(e -> mapController.showMapView());
        addButton.setOnAction(e -> mapController.onAddServiceFromList());

        sortByResponseItem.setOnAction(e -> loadAndSortByResponseTime());

        syncRowsWithSortedList();
    }

    private void syncRowsWithSortedList()
    {
        currentServers.clear();
        for (ServerResult s : mapController.getMasterServers())
        {
            currentServers.put(s.id(), s);
        }

        serverRowsById.keySet().removeIf(id -> !currentServers.containsKey(id));
        if (selectedServerId != null && !currentServers.containsKey(selectedServerId))
        {
            selectedServerId = null;
            detailsScrollPane.setVisible(false);
            detailsScrollPane.setManaged(false);
        }

        serversContainer.getChildren().clear();
        for (ServerResult server : sortedServers)
        {
            ServerRowView rowView = serverRowsById.computeIfAbsent(
                    server.id(),
                    id -> createServerRowView(server)
            );

            if (rowView.currentStatus != server.status())
            {
                updateCircleColor(rowView.statusIndicator, server.status());
                rowView.currentStatus = server.status();

                if (selectedServerId != null && selectedServerId == server.id())
                {
                    showServerDetails(server);
                }
            }

            serversContainer.getChildren().add(rowView.container);
        }
    }

    private void updateFilterPredicate(String query)
    {
        String normalizedQuery = (query == null) ? "" : query.trim().toLowerCase();

        filteredServers.setPredicate(server -> {
            if (normalizedQuery.isEmpty())
            {
                return true;
            }

            boolean matchesName = server.name() != null
                    && server.name().toLowerCase().contains(normalizedQuery);
            boolean matchesAddress = server.address() != null
                    && server.address().toLowerCase().contains(normalizedQuery);

            return matchesName || matchesAddress;
        });
    }

    private ServerRowView createServerRowView(ServerResult server)
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
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        serverRow.getChildren().addAll(statusIndicator, nameLabel);

        serverRow.setOnMouseClicked(e -> {
            selectedServerId = server.id();
            ServerResult current = currentServers.getOrDefault(server.id(), server);
            showServerDetails(current);
        });

        return new ServerRowView(serverRow, statusIndicator, nameLabel, null, server.status());
    }

    private void loadAndSortByResponseTime()
    {
        Task<Map<Integer, Double>> task = new Task<>()
        {
            @Override
            protected Map<Integer, Double> call() throws Exception
            {
                Map<Integer, Double> averages = new HashMap<>();
                for (ServerResult s : mapController.getMasterServers())
                {
                    Double avg = PingHistory.AverageResponseTime(s.id(), 10);
                    averages.put(s.id(), avg != null ? avg : -1.0);
                }
                return averages;
            }
        };

        task.setOnSucceeded(e -> {
            Map<Integer, Double> averages = task.getValue();
            sortedServers.setComparator(
                Comparator.comparingDouble((ServerResult s) -> averages.getOrDefault(s.id(), -1.0)).reversed()
            );
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            Viewer.showErrorAlert("Ошибка", "Не удалось отсортировать: " + task.getException().getMessage());
        });

        new Thread(task).start();
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