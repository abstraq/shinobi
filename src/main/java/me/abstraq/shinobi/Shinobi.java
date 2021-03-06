/*
 * Shinobi
 * Copyright (C) 2021 Abstraq Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.abstraq.shinobi;

import java.io.Console;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.security.auth.login.LoginException;
import me.abstraq.shinobi.commands.CommandDispatcher;
import me.abstraq.shinobi.commands.WarnCommand;
import me.abstraq.shinobi.database.DatabaseProvider;
import me.abstraq.shinobi.services.PromptService;
import net.dv8tion.jda.api.GatewayEncoding;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for Shinobi.
 */
public final class Shinobi extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(Shinobi.class);
    private final JDA api;
    private final CommandDispatcher commandDispatcher;
    private final DatabaseProvider databaseProvider;
    private final ExecutorService executorService;
    private final ScheduledExecutorService schedulerService;
    private final PromptService promptService;

    Shinobi() {
        try {
            this.api = JDABuilder.createLight(System.getenv("DISCORD_TOKEN"))
                .setActivity(Activity.watching("over this server."))
                .setGatewayEncoding(GatewayEncoding.ETF)
                .addEventListeners(this)
                .build();
        } catch (LoginException e) {
            logger.error("Error while starting shinobi: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        this.commandDispatcher = new CommandDispatcher(this);

        this.databaseProvider = new DatabaseProvider(
            System.getenv("PG_HOST"),
            5432,
            System.getenv("PG_USER"),
            System.getenv("PG_PASS"),
            System.getenv("PG_DBNAME")
        );

        this.executorService = Executors.newCachedThreadPool();

        this.schedulerService = Executors.newScheduledThreadPool(10);

        this.promptService = new PromptService();

        this.api().addEventListener(this.commandDispatcher);
        this.api().addEventListener(this.promptService);
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        this.registerCommands();
        logger.info("Shinobi initialization complete, ready to serve.");
    }

    public JDA api() {
        return this.api;
    }

    public CommandDispatcher commandDispatcher() {
        return this.commandDispatcher;
    }

    public DatabaseProvider databaseProvider() {
        return this.databaseProvider;
    }

    public ExecutorService executorService() {
        return this.executorService;
    }

    public ScheduledExecutorService schedulerService() {
        return this.schedulerService;
    }

    public PromptService promptService() {
        return this.promptService;
    }

    private void registerCommands() {
        this.commandDispatcher().register(856920316927672352L, WarnCommand.class);
    }

    /**
     * Entry point for shinobi.
     *
     * @param args arguments passed to the program by the user.
     */
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            logger.warn("Could not get console, starting shinobi in non-interactive mode.");
        } else {
            logger.info("Starting shinobi in interactive mode.");
            logger.info("Available commands: 'stop'.");
        }

        var client = new Shinobi();

        while (true) {
            if (console != null) {
                String command = console.readLine();
                if (command.equalsIgnoreCase("stop")) {
                    logger.info("Received stop command, shutting down the client.");
                    client.api.shutdown();
                    System.exit(0);
                }
            }
        }
    }
}
