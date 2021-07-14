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

package me.abstraq.shinobi.commands;

import me.abstraq.shinobi.database.model.GuildRecord;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

/**
 * Represents a command in Shinobi.
 */
public interface Command {

    /**
     * Callback that is run when the SlashCommandEvent is received and passes all the predicates.
     *
     * @param event   context object of the event.
     * @param guildRecord the record of this
     */
    void execute(SlashCommandEvent event, GuildRecord guildRecord, Guild guild, TextChannel channel, Member sender);
}
