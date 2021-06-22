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

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

/**
 * Represents a command in Shinobi.
 */
public interface Command {

    /**
     * Callback that is ran when the SlashCommandEvent is received and passes all of the predicates.
     *
     * @param event   context object of the event.
     * @param guild   the guild the event was fired in.
     * @param channel the channel the event was fired in.
     * @param sender  the person who sent the command.
     */
    void execute(SlashCommandEvent event, Guild guild, TextChannel channel, Member sender);
}
