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
import java.util.concurrent.CompletableFuture;
import me.abstraq.shinobi.Shinobi;
import me.abstraq.shinobi.database.model.GuildRecord;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
        long commandID = event.getCommandIdLong();
        Guild guild = event.getGuild();

        // Check if the command is dispatched from a Guild or a DMChannel.
        if (guild == null) {
            event.reply("Shinobi commands currently only support guilds.").queue();
            return;
        }

        long guildID = guild.getIdLong();

        Member sender = event.getMember();
        assert sender != null : "Interaction is missing a member property.";

        this.logger.info("{} triggered interaction {} for command {} in guild {}.", sender.getId(), event.getId(), commandID, guildID);

        Command command = this.storage.get(commandID);

        // Check if shinobi contains an implementation for the requested command.
        if (command == null) {
            this.logger.warn("Failed to dispatch command {} in guild {} due to missing implementation.", commandID, guildID);
            event.reply("This command is not yet implemented.").setEphemeral(true).queue();
            return;
        }

        CompletableFuture.runAsync(() -> {
            // Get the guild record or create it if there is none.
            try {
                GuildRecord guildRecord = this.client.databaseProvider().retrieveGuild(guildID);
                if (guildRecord == null) {
                    this.client.databaseProvider().createGuild(guildID);
                    guildRecord = new GuildRecord(guildID, null, null, GuildRecord.GuildStatus.ACTIVE);
                }

                // Don't dispatch command because guild is disabled.
                if (guildRecord.status() == GuildRecord.GuildStatus.DISABLED) {
                    this.logger.info("Failed to dispatch command {} in guild {} due to the guild being disabled.", commandID, guildID);
                    event.reply("Shinobi is disabled in this guild. Contact Shinobi support if you believe this is an error.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }

                // Dispatch the command.
                this.logger.info("Dispatching command {} in guild {}.", commandID, guildID);
                command.execute(event, guildRecord, guild, event.getTextChannel(), sender);
            } catch (Exception e) {
                this.logger.error(e.getMessage());
                event.reply("An error occurred while dispatching this command. Try again later.")
                        .setEphemeral(true)
                        .queue();
            }
        }, this.client.executorService());
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
}
