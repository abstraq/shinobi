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

package me.abstraq.shinobi.services;

import me.abstraq.shinobi.database.model.CaseRecord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.TextChannel;

/**
 * Handles sending notification embeds to various channels
 * after a moderator action. Does not check if the user has
 * permission to send embeds in the channel so the checks
 * have to be handled before using this service.
 */
public class NotificationService {

    /**
     * Publishes a notification to the private message channel of the targeted user.
     *
     * @param caseRecord {@link CaseRecord} of the case this notification is for.
     * @param channel the target's private message channel.
     * @param guild the guild of this case.
     * @param moderator the moderator who created this case.
     */
    public static void publishPrivateMessage(CaseRecord caseRecord, PrivateChannel channel, Guild guild, Member moderator) {
        String actionPastTense = caseRecord.type().name().toLowerCase() + "ed";
        String guildName = guild.getName();
        String moderatorTag = moderator.getUser().getAsTag();

        String description = String.format("You have been %s by **%s** `%s` in guild **%s** `%s` on %s.",
            actionPastTense,
            moderatorTag,
            caseRecord.moderatorID(),
            guildName,
            caseRecord.guildID(),
            String.format("<t:%s:F>", caseRecord.createdAt().getEpochSecond())
        );

        var embed = new EmbedBuilder()
            .setAuthor(guildName, null, guild.getIconUrl())
            .setColor(0x5a0404)
            .setDescription(description)
            .setFooter("Case #" + caseRecord.id());

        if (caseRecord.reason() != null) {
            embed.addField("Reason", caseRecord.reason(), false);
        }

        if (caseRecord.expiresAt() != null) {
            var expiration = String.format("<t:%s:F>", caseRecord.expiresAt().getEpochSecond());
            embed.addField("Expiration", expiration, false);
        }

        channel.sendMessageEmbeds(embed.build())
            .queue(sentMessage -> channel.close().queue());
    }

    /**
     * Publish a notification to the mod log channel of the guild.
     *
     * @param caseRecord {@link CaseRecord} of the case this notification is for.
     * @param channel the mod log channel of the guild.
     * @param target the target of the action.
     * @param moderator the moderator who performed the action.
     */
    public static void publishModLog(CaseRecord caseRecord, TextChannel channel, Member target, Member moderator) {
        String actionName = caseRecord.type().name();
        String actionPastTense = actionName.charAt(0) + actionName.substring(1).toLowerCase() + "ed";
        String targetTag = target.getUser().getAsTag();
        String moderatorTag = moderator.getUser().getAsTag();

        var embed = new EmbedBuilder()
            .setAuthor(actionPastTense + " Member")
            .setDescription("//////////////////////////////////////")
            .setColor(0x5a0404)
            .addField("Member", String.format("%s `%s`", targetTag, target.getId()), false)
            .addField("Moderator", String.format("%s `%s`", moderatorTag, moderator.getId()), false)
            .addField("Creation", String.format("<t:%s:F>", caseRecord.createdAt().getEpochSecond()), false)
            .setFooter("Case #" + caseRecord.id());

        if (caseRecord.expiresAt() != null) {
            var expiration = String.format("<t:%s:F>", caseRecord.expiresAt().getEpochSecond());
            embed.addField("Expiration", expiration, false);
        }

        if (caseRecord.reference() != null) {
            embed.addField("Reference", String.format("Case #%s", caseRecord.reference()), false);
        }

        if (caseRecord.reason() != null) {
            embed.addField("Reason", caseRecord.reason(), false);
        }

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Publish a notification to the channel the command was ran in.
     *
     * @param caseRecord {@link CaseRecord} of the case this notification is for.
     * @param channel the channel the command was ran in.
     * @param target the target of the action.
     * @param moderator the moderator who performed the action.
     */
    public static void publishChannelBroadcast(CaseRecord caseRecord, TextChannel channel, Member target, Member moderator) {
        String actionPastTense = caseRecord.type().name().toLowerCase() + "ed";
        String targetTag = target.getUser().getAsTag();
        String moderatorTag = moderator.getUser().getAsTag();

        String description = String.format("**%s** `%s` has been %s by **%s** `%s` for *%s*.",
            targetTag,
            caseRecord.targetID(),
            actionPastTense,
            moderatorTag,
            caseRecord.moderatorID(),
            caseRecord.reason()
        );
        var embed = new EmbedBuilder()
            .setDescription(description)
            .setColor(0x2F3136)
            .setFooter("Case #" + caseRecord.id())
            .build();
        channel.sendMessageEmbeds(embed).queue();
    }
}
