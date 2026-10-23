package ru.miet.activitycontroller.core;

import java.util.List;
import java.sql.SQLException;

public class Service {

    public static void update(Database database) throws SQLException {

        if (database == null) return;

        List<Database.ServerResult> servers = database.getServers();

        for (Database.ServerResult server : servers) {
            Pinger.PingResult new_ping = Pinger.runPing(server.address());
            ServerStatus new_status = ServerStatus.DetermineServerStatus(new_ping);
            if (new_status != server.status()) {
                database.updateServerStatus(server.id(),  new_status);
            }
        }
    }
}
