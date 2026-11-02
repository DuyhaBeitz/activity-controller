package ru.miet.activitycontroller.core;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Service {

    private static final ExecutorService PING_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    public static CompletableFuture<Void> update(Database database)
            throws SQLException {

        if (database == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<Database.ServerResult> servers = database.getServers();

        List<CompletableFuture<Void>> futures = servers.stream()
                .map(server -> CompletableFuture
                        .supplyAsync(() -> {
                            Pinger.PingResult ping =
                                    Pinger.runPing(server.address());

                            return ServerStatus.DetermineServerStatus(ping);
                        }, PING_EXECUTOR)
                        .thenAcceptAsync(newStatus -> {
                            if (server.status() == ServerStatus.CONTROL
                                    && newStatus != ServerStatus.OK) {
                                return;
                            }

                            if (newStatus != server.status()) {
                                try {
                                    database.updateServerStatus(
                                            server.id(), newStatus);
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        })
                )
                .toList();

        return CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
    }
}