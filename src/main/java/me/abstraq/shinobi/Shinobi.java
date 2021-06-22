/*
 * Copyright (C) 2021 Abstraq Software
 *
 * Shinobi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shinobi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Shinobi. If not, see <http://www.gnu.org/licenses/>.
 */

package me.abstraq.shinobi;

import javax.security.auth.login.LoginException;
import me.abstraq.shinobi.commands.CommandDispatcher;
import net.dv8tion.jda.api.GatewayEncoding;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for Shinobi.
 */
public final class Shinobi {
    private final Logger logger;
    private final JDA api;
    private final CommandDispatcher commandDispatcher;

    Shinobi() {
        this.logger = LoggerFactory.getLogger(Shinobi.class);

        try {
            this.api = JDABuilder.createLight(System.getenv("DISCORD_TOKEN"))
                .setActivity(Activity.watching("over this server."))
                .setGatewayEncoding(GatewayEncoding.ETF)
                .build();
        } catch (LoginException e) {
            this.logger.error("Error while starting shinobi: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        this.commandDispatcher = new CommandDispatcher(this);
        this.api.addEventListener(this.commandDispatcher);
        this.registerCommands();
    }

    public JDA api() {
        return this.api;
    }

    public CommandDispatcher commandDispatcher() {
        return this.commandDispatcher;
    }

    private void registerCommands() {
        // Register commands here.
    }

    /**
     * Entry point for shinobi.
     *
     * @param args arguments passed to the program by the user.
     */
    public static void main(String[] args) {
        new Shinobi();
    }
}
