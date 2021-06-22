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

package me.abstraq.shinobi.commands;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import me.abstraq.shinobi.Shinobi;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles dispatching SlashCommandEvents to the appropriate commands.
 */
public final class CommandDispatcher extends ListenerAdapter {
    private final Shinobi client;
    private final HashMap<Long, Command> storage;
    private final Logger logger;

    /**
     * Constructor for CommandDispatcher.
     *
     * @param client Shinobi client.
     */
    public CommandDispatcher(Shinobi client) {
        this.client = client;
        this.logger = LoggerFactory.getLogger(CommandDispatcher.class);
        this.storage = new HashMap<>();
    }

    /**
     * Dispatches the command event to the registered command if
     * it is present.
     *
     * @param event The SlashCommandEvent to dispatch.
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        // Check if the command is dispatched from a Guild or a DMChannel.
        if (event.getGuild() == null) {
            event.reply("Shinobi commands currently only support guilds.").queue();
            return;
        }

        var id = event.getCommandIdLong();
        var command = this.storage.get(id);
        var guildId = event.getGuild().getIdLong();

        this.logger.info("Received command {} in guild {}.", id, guildId);

        // Check if shinobi contains an implementation for the requested command.
        if (command == null) {
            this.logger.warn("Command {} is missing an implementation.", id);
            event.reply("This command is not yet implemented.").setEphemeral(true).queue();
            return;
        }

        command.execute(event, event.getGuild(), event.getTextChannel(), event.getMember());
    }

    /**
     * Register a command to the dispatcher.
     *
     * @param id      The ID of the command you want to register.
     * @param command The command object to register.
     */
    public void register(long id, Class<? extends Command> command) {
        try {
            this.storage.putIfAbsent(id, command.getDeclaredConstructor(Shinobi.class).newInstance(this.client));
            this.logger.info("Registered command '{}'.", id);
        } catch (NoSuchMethodException e) {
            this.logger.warn("Failed to register command {}: The constructor does not accept a client instance.", id);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            this.logger.warn("Failed to register command {}: {}", id, e.getMessage());
        }
    }

    protected Logger logger() {
        return this.logger;
    }
}