package ru.miet.activitycontroller.ui;

import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import ru.miet.activitycontroller.core.ServerStatus;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

public class ServerRowView
{
    HBox container;
    Circle statusIndicator;
    Label nameLabel;
    Button detailsButton;
    ServerStatus currentStatus;

    public ServerRowView(HBox container, Circle statusIndicator, Label nameLabel, Button detailsButton, ServerStatus initialStatus)
    {
        this.container = container;
        this.statusIndicator = statusIndicator;
        this.nameLabel = nameLabel;
        this.detailsButton = detailsButton;
        this.currentStatus = initialStatus;
    }
}
