package ru.miet.activitycontroller.ui;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.util.StringConverter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

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

    @FXML private MenuItem sortByStatusItem;
    @FXML private MenuItem sortByResponseItem;
    @FXML private MenuItem sortByNameItem;
    @FXML private MenuItem sortByDateItem;
    @FXML private MenuItem sortSmartItem;
    @FXML private MenuItem sortByStabilityItem;
    @FXML private Button reverseSortButton;

    @FXML private ScrollPane detailsScrollPane;
    @FXML private VBox detailsPanel;
    @FXML private Label detailsTitle;
    @FXML private Label detailsStatus;
    @FXML private VBox notesBox;
    @FXML private Label notesLabel;
    @FXML private TextArea detailsNotes;

    // Поля блока графика
    @FXML private VBox chartSection;
    @FXML private ComboBox<String> metricComboBox;
    @FXML private ToggleButton range15mBtn;
    @FXML private ToggleButton range1hBtn;
    @FXML private ToggleButton range24hBtn;
    @FXML private ToggleButton range7dBtn;

    @FXML private Label avgStatLabel;
    @FXML private Label minStatLabel;
    @FXML private Label maxStatLabel;
    @FXML private Label uptimeStatLabel;

    @FXML private LineChart<Number, Number> activityChart;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;

    @FXML private ComboBox<String> yScaleComboBox;
    @FXML private Button liveModeBtn;

    private boolean isLiveFollow = true;
    private long currentWindowMillis = TimeUnit.MINUTES.toMillis(15); // По умолчанию 15 мин
    private double lastMouseX;
    private Timeline liveAxisTicker;

    private MapController mapController;

    private FilteredList<ServerResult> filteredServers;
    private SortedList<ServerResult> sortedServers;

    private final Map<Integer, ServerRowView> serverRowsById = new HashMap<>();
    private final Map<Integer, ServerResult> currentServers = new HashMap<>();
    private Integer selectedServerId = null;

    private final Map<Integer, Double> avgResponseCache = new ConcurrentHashMap<>();
    private boolean isResponseTimeSortActive = false;

    private enum SortMode
    {
        NONE,
        STATUS,
        RESPONSE_TIME,
        NAME,
        DATE_ADDED,
        SMART,
        STABILITY
    }

    private SortMode currentSortMode = SortMode.NONE;
    private boolean isSortReversed = false;

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


        sharedList.addListener((ListChangeListener<ServerResult>) change -> {
            if (isResponseTimeSortActive)
            {
                refreshResponseTimeCacheAsync();
            }
        });

        backButton.setOnAction(e -> mapController.showMapView());
        addButton.setOnAction(e -> mapController.onAddServiceFromList());

        sortByStatusItem.setOnAction(e -> selectSortMode(SortMode.STATUS, "По тяжести статуса"));
        sortByResponseItem.setOnAction(e -> selectSortMode(SortMode.RESPONSE_TIME, "По времени ответа"));
        sortByNameItem.setOnAction(e -> selectSortMode(SortMode.NAME, "По имени"));
        sortByDateItem.setOnAction(e -> selectSortMode(SortMode.DATE_ADDED, "По дате добавления"));
        sortSmartItem.setOnAction(e -> selectSortMode(SortMode.SMART, "Умная сортировка"));
        sortByStabilityItem.setOnAction(e -> selectSortMode(SortMode.STABILITY, "По стабильности"));

        reverseSortButton.setOnAction(e -> toggleSortDirection());

        syncRowsWithSortedList();
        initChartControls();
    }

    private void selectSortMode(SortMode mode, String buttonTitle)
    {
        currentSortMode = mode;
        isSortReversed = false;
        reverseSortButton.setText("▼");
        sortButton.setText(buttonTitle);

        isResponseTimeSortActive = (mode == SortMode.RESPONSE_TIME || mode == SortMode.SMART);

        applyCurrentSort();
    }

    private void toggleSortDirection()
    {
        if (currentSortMode == SortMode.NONE)
        {
            return;
        }

        isSortReversed = !isSortReversed;
        reverseSortButton.setText(isSortReversed ? "▲" : "▼");

        var current = sortedServers.getComparator();
        if (current != null)
        {
            sortedServers.setComparator(current.reversed());
        }
    }

    private void applyCurrentSort()
    {
        switch (currentSortMode)
        {
            case STATUS -> activateStatusSort();
            case RESPONSE_TIME -> activateResponseTimeSort();
            case NAME -> activateNameSort();
            case DATE_ADDED -> activateDateAddedSort();
            case SMART -> activateSmartSort();
            case STABILITY -> activateStabilitySort();
            case NONE -> sortedServers.setComparator(null);
        }
    }

    private void activateStatusSort()
    {
        var comp = Comparator.comparingInt(this::getStatusWeight);
        sortedServers.setComparator(isSortReversed ? comp.reversed() : comp);
    }

    private int getStatusWeight(ServerResult server)
    {
        return switch (server.status())
        {
            case ERROR -> 1;
            case CONTROL -> 2;
            case WARNING -> 3;
            case OK -> 4;
        };
    }

    private void activateResponseTimeSort()
    {
        isResponseTimeSortActive = true;

        var comp = Comparator.comparingDouble((ServerResult s) -> avgResponseCache.getOrDefault(s.id(), -1.0)).reversed();
        sortedServers.setComparator(isSortReversed ? comp.reversed() : comp);
        refreshResponseTimeCacheAsync();
    }

    private void activateNameSort()
    {
        var comp = Comparator.comparing(ServerResult::name, String.CASE_INSENSITIVE_ORDER);
        sortedServers.setComparator(isSortReversed ? comp.reversed() : comp);
    }

    private void activateDateAddedSort()
    {
        var comp = Comparator.comparingInt(ServerResult::id).reversed();
        sortedServers.setComparator(isSortReversed ? comp.reversed() : comp);
    }

    private void activateSmartSort()
    {
        isResponseTimeSortActive = true;

        Comparator<ServerResult> comp = Comparator
            .comparingInt(this::getStatusWeight)
            .thenComparing(
                Comparator.comparingDouble((ServerResult s) -> avgResponseCache.getOrDefault(s.id(), -1.0)).reversed()
            );

        sortedServers.setComparator(isSortReversed ? comp.reversed() : comp);
        refreshResponseTimeCacheAsync();
    }

    private void activateStabilitySort()
    {
        // TODO: реализовать сортировку по стабильности (% успешных пингов)
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

    private void refreshResponseTimeCacheAsync()
    {
        // Берём копию списка серверов, чтобы безопасно читать в фоновом потоке
        List<ServerResult> snapshot = List.copyOf(mapController.getMasterServers());

        Task<Map<Integer, Double>> task = new Task<>()
        {
            @Override
            protected Map<Integer, Double> call() throws Exception
            {
                Map<Integer, Double> freshAverages = new HashMap<>();
                for (ServerResult s : snapshot)
                {
                    Double avg = PingHistory.AverageResponseTime(s.id(), 10);
                    freshAverages.put(s.id(), avg != null ? avg : -1.0);
                }
                return freshAverages;
            }
        };

        task.setOnSucceeded(e -> {
            avgResponseCache.clear();
            avgResponseCache.putAll(task.getValue());

            // Принудительно заставляем SortedList пересчитать порядок по обновлённому кэшу
            if (isResponseTimeSortActive)
            {
                var currentComp = sortedServers.getComparator();
                sortedServers.setComparator(null);
                sortedServers.setComparator(currentComp);
            }
        });

        new Thread(task).start();
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

    private void initChartControls()
    {
        metricComboBox.getItems().setAll(
                "Общее время ответа (с)",
                "Время до первого байта / TTFB (с)",
                "DNS-запрос (с)",
                "TCP-подключение (с)",
                "TLS-рукопожатие (с)",
                "Скорость загрузки (байт/с)",
                "HTTP-код ответа"
        );
        metricComboBox.getSelectionModel().selectFirst();

        metricComboBox.setOnAction(e -> {
            String selectedMetric = metricComboBox.getValue();
            if (selectedMetric != null)
            {
                updateYScaleOptionsForMetric(selectedMetric);
            }
        });

        ToggleGroup rangeGroup = new ToggleGroup();
        range15mBtn.setToggleGroup(rangeGroup);
        range1hBtn.setToggleGroup(rangeGroup);
        range24hBtn.setToggleGroup(rangeGroup);
        range7dBtn.setToggleGroup(rangeGroup);
        range15mBtn.setOnAction(e -> setChartWindow(TimeUnit.MINUTES.toMillis(15)));
        range1hBtn.setOnAction(e -> setChartWindow(TimeUnit.HOURS.toMillis(1)));
        range24hBtn.setOnAction(e -> setChartWindow(TimeUnit.HOURS.toMillis(24)));
        range7dBtn.setOnAction(e -> setChartWindow(TimeUnit.DAYS.toMillis(7)));

        // Запрещаем снимать выделение со всех кнопок сразу (один период всегда активен)
        rangeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null)
            {
                oldToggle.setSelected(true);
            }
        });

        activityChart.setMinHeight(LayoutScale.scaled(260.0));
        activityChart.setPrefHeight(LayoutScale.scaled(300.0));

        setupAxes();
        setupYScaleControl();
        setupChartPanning();
        startLiveAxisTicker();
    }



    private void startLiveAxisTicker()
    {
        if (liveAxisTicker != null)
        {
            liveAxisTicker.stop();
        }

        liveAxisTicker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            // Двигаем ось X только если:
            // 1. Вторая страница открыта (видна)
            // 2. Выбран какой-то сервер (открыта панель деталей)
            // 3. Включён режим Live (пользователь не отмотал график назад)
            if (rootPane.isVisible() && detailsScrollPane.isVisible() && isLiveFollow)
            {
                long now = System.currentTimeMillis();
                xAxis.setLowerBound(now - currentWindowMillis);
                xAxis.setUpperBound(now);
                xAxis.setTickUnit(currentWindowMillis / 6.0);
            }
        }));

        liveAxisTicker.setCycleCount(Timeline.INDEFINITE);
        liveAxisTicker.play();
    }

    // Форматирование времени на оси X (из миллисекунд в ЧЧ:ММ:СС)
    private void setupAxes()
    {
        xAxis.setAutoRanging(false);
        xAxis.setForceZeroInRange(false);

        DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter longFmt = DateTimeFormatter.ofPattern("dd.MM HH:mm");

        xAxis.setTickLabelFormatter(new StringConverter<Number>()
        {
            @Override
            public String toString(Number millis)
            {
                LocalDateTime time = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(millis.longValue()),
                        ZoneId.systemDefault()
                );
                DateTimeFormatter fmt = (currentWindowMillis >= TimeUnit.HOURS.toMillis(24))
                        ? longFmt
                        : shortFmt;
                return time.format(fmt);  
            }

            @Override
            public Number fromString(String string)
            {
                return 0;
            }
        });

        // Устанавливаем начальное окно от (сейчас - 15 мин) до (сейчас)
        snapToLive();
    }

    // Управление потолком вертикальной оси Y
    private void setupYScaleControl()
    {
        updateYScaleOptionsForMetric(metricComboBox.getValue());

        yScaleComboBox.setOnAction(e -> {
            String selected = yScaleComboBox.getValue();
            if (selected == null || yScaleComboBox.isDisabled())
            {
                return;
            }

            if (selected.equals("Ось Y: Авто"))
            {
                yAxis.setAutoRanging(true);
            }
            else
            {
                // Вытаскиваем число из строк вида "Макс: 3.0 с" или "Макс: 1024 КБ/с"
                double maxVal = Double.parseDouble(selected.replaceAll("[^0-9.]", ""));
                yAxis.setAutoRanging(false);
                yAxis.setLowerBound(0.0);
                yAxis.setUpperBound(maxVal);
                yAxis.setTickUnit(maxVal / 5.0);
            }
        });
    }

    // Горизонтальная прокрутка перетаскиванием мыши (Drag-to-Pan) и кнопка Live
    private void setupChartPanning()
    {
        liveModeBtn.setOnAction(e -> snapToLive());

        activityChart.setOnMousePressed(e -> {
            lastMouseX = e.getX();
        });

        activityChart.setOnMouseDragged(e -> {
            double deltaX = e.getX() - lastMouseX;
            lastMouseX = e.getX();

            double axisWidth = xAxis.getWidth();
            if (axisWidth <= 0) return;

            double visibleMillis = xAxis.getUpperBound() - xAxis.getLowerBound();
            double millisPerPixel = visibleMillis / axisWidth;
            double shiftMillis = deltaX * millisPerPixel;

            long now = System.currentTimeMillis();
            double newUpper = xAxis.getUpperBound() - shiftMillis;
            double newLower = xAxis.getLowerBound() - shiftMillis;

            // Не пускаем правую границу в будущее (дальше текущего момента)
            if (newUpper >= now)
            {
                newUpper = now;
                newLower = now - visibleMillis;
                setLiveFollow(true);
            }
            else
            {
                setLiveFollow(false);
            }

            xAxis.setLowerBound(newLower);
            xAxis.setUpperBound(newUpper);
            xAxis.setTickUnit(visibleMillis / 6.0);
        });
    }

    // Возврат графика в конец (к текущему времени)
    private void snapToLive()
    {
        setLiveFollow(true);
        long now = System.currentTimeMillis();
        xAxis.setLowerBound(now - currentWindowMillis);
        xAxis.setUpperBound(now);
        xAxis.setTickUnit(currentWindowMillis / 6.0);
    }

    private void setLiveFollow(boolean live)
    {
        this.isLiveFollow = live;
        if (live)
        {
            liveModeBtn.setText("● Live");
            liveModeBtn.getStyleClass().remove("paused");
        }
        else
        {
            liveModeBtn.setText("Live ➔");
            if (!liveModeBtn.getStyleClass().contains("paused"))
            {
                liveModeBtn.getStyleClass().add("paused");
            }
        }
    }

    private void setChartWindow(long windowMillis)
    {
        this.currentWindowMillis = windowMillis;
        snapToLive();
    }

    private void updateYScaleOptionsForMetric(String metricName)
    {
        if (metricName == null) return;

        yScaleComboBox.setDisable(false);
        yAxis.setAutoRanging(true);

        if (metricName.contains("HTTP-код"))
        {
            yScaleComboBox.getItems().setAll("Фикс: 0–600");
            yScaleComboBox.getSelectionModel().selectFirst();
            yScaleComboBox.setDisable(true);

            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(0.0);
            yAxis.setUpperBound(600.0);
            yAxis.setTickUnit(100.0);
        }
        else if (metricName.contains("Скорость загрузки"))
        {
            yScaleComboBox.getItems().setAll(
                    "Ось Y: Авто",
                    "Макс: 100 КБ/с",
                    "Макс: 1024 КБ/с",
                    "Макс: 10240 КБ/с"
            );
            yScaleComboBox.getSelectionModel().selectFirst();
        }
        else
        {
            // Для всех временных параметров (секунды)
            yScaleComboBox.getItems().setAll(
                    "Ось Y: Авто",
                    "Макс: 0.5 с",
                    "Макс: 1.0 с",
                    "Макс: 3.0 с",
                    "Макс: 10.0 с"
            );
            yScaleComboBox.getSelectionModel().selectFirst();
        }
    }

    public void stopLiveAxisTicker()
    {
        if (liveAxisTicker != null)
        {
            liveAxisTicker.stop();
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