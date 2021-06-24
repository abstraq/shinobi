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

import java.time.Instant;
import java.util.Optional;
import me.abstraq.shinobi.Shinobi;
import me.abstraq.shinobi.database.DatabaseProvider;
import me.abstraq.shinobi.database.model.CaseRecord;
import me.abstraq.shinobi.database.model.GuildRecord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a warning to a user.
 *
 * <p>Usage: {@code /warn <target> [reason] [reference] [silent]}
 * <p>
 * target - {@link User} representing the target of the command.<br>
 * reason - Optional string representing the reason for the warning.<br>
 * reference - Optional long representing another case ID to link this case to.<br>
 * silent - Optional boolean that indicates no broadcast should be sent to the current channel when true.
 * </p>
 */
public final class WarnCommand implements Command {
    private final Shinobi client;
    private final DatabaseProvider database;
    private final Logger logger;

    WarnCommand(Shinobi client) {
        this.client = client;
        this.logger = LoggerFactory.getLogger(WarnCommand.class);
        this.database = this.client.databaseProvider();
    }

    @Override
    public void execute(SlashCommandEvent event, GuildRecord guildRecord, Guild guild, TextChannel channel, Member sender) {
        OptionMapping targetOption = event.getOption("target");

        if (targetOption == null) {
            this.logger.warn("Command interaction for warn command was missing target property which is required.");
            event.reply("There was an error processing this command, contact Shinobi support if this issue persists.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member target = targetOption.getAsMember();

        // The sender isn't able to action the target.
        if (target == null || !sender.canInteract(target)) {
            event.reply("""
                    You are unable to perform moderation actions on the target user.
                    This occurs if the user is not in this guild, or if they have a higher permission hierarchy position than you.
                    """)
                .setEphemeral(true)
                .queue();
            return;
        }

        // Shinobi isn't able to action the target.
        if (!guild.getSelfMember().canInteract(target)) {
            event.reply("""
                    Shinobi is unable to perform moderation actions on the target user.
                    This occurs if the user has a higher permission hierarchy position than Shinobi.
                    """)
                .setEphemeral(true)
                .queue();
            return;
        }

        String reason = Optional.ofNullable(event.getOption("reason"))
            .map(OptionMapping::getAsString)
            .orElse(null);

        if (reason != null && reason.length() > 255) {
            event.reply("Your reason must be a max of 255 characters.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Long reference = Optional.ofNullable(event.getOption("reference"))
            .map(OptionMapping::getAsLong)
            .orElse(null);

        var guildID = guild.getIdLong();
        var targetID = target.getIdLong();
        var moderatorID = sender.getIdLong();
        var createdAt = Instant.now();

        if (reference != null) {
            this.database.retrieveCaseBySeq(guildID, reference)
                .thenApply(referenceCase -> {
                    if (referenceCase == null) {
                        throw new RuntimeException("The case '" + reference + "' that you tried to reference does not exist.");
                    }
                    return referenceCase;
                })
                .thenCompose(referenceCase -> this.database.createCase(CaseRecord.CaseType.WARN, guildID, targetID, moderatorID, reason, createdAt, null, reference))
                .thenAccept(caseSeq -> {
                    target.getUser().openPrivateChannel().queue(dm -> {
                        if (dm != null) {
                            dm.sendMessageEmbeds(this.dmEmbed(caseSeq, guild, target, sender, reason, createdAt)).queue();
                        }
                    });
                    event.replyEmbeds(this.broadcastEmbed(caseSeq, target, sender, reason, createdAt, reference)).queue();
                })
                .exceptionally(ex -> {
                    event.reply(ex.getCause().getMessage())
                        .setEphemeral(true)
                        .queue();
                    return null;
                });
            return;
        }

        this.database.createCase(CaseRecord.CaseType.WARN, guildID, targetID, moderatorID, reason, createdAt, null, null)
            .thenAccept(caseSeq -> {
                target.getUser().openPrivateChannel().queue(dm -> {
                    if (dm != null) {
                        dm.sendMessageEmbeds(this.dmEmbed(caseSeq, guild, target, sender, reason, createdAt)).queue();
                    }
                });
                event.replyEmbeds(this.broadcastEmbed(caseSeq, target, sender, reason, createdAt, null)).queue();
            });
    }

    private MessageEmbed dmEmbed(long caseSeq, Guild guild, Member target, Member moderator, String reason, Instant createdAt) {
        String creationTimestamp = String.format("<t:%s:F>", createdAt.getEpochSecond());
        StringBuilder descriptionBuilder = new StringBuilder("You were warned in **")
            .append(guild.getName())
            .append("** by **")
            .append(moderator.getUser().getAsTag())
            .append("** on ")
            .append(creationTimestamp);

        if (reason != null) {
            descriptionBuilder
                .append(" for **")
                .append(reason)
                .append("**");
        }
        descriptionBuilder.append(".");
        return new EmbedBuilder()
            .setColor(0x2F3136)
            .setAuthor(guild.getName(), null, guild.getIconUrl())
            .setDescription(descriptionBuilder)
            .setFooter("Case #" + caseSeq)
            .build();
    }

    private MessageEmbed broadcastEmbed(long caseSeq, Member target, Member moderator, String reason, Instant createdAt, Long reference) {
        String creationTimestamp = String.format("<t:%s:F>", createdAt.getEpochSecond());
        StringBuilder descriptionBuilder = new StringBuilder("**")
            .append(moderator.getUser().getAsTag())
            .append("** warned **")
            .append(target.getUser().getAsTag())
            .append("** on ")
            .append(creationTimestamp);

        if (reason != null) {
            descriptionBuilder
                .append(" for **")
                .append(reason)
                .append("**");
        }
        if (reference != null) {
            descriptionBuilder
                .append(" citing case #")
                .append(reference)
                .append(" as a reference");
        }
        descriptionBuilder.append(".");

        return new EmbedBuilder()
            .setColor(0x2F3136)
            .setAuthor("Warned User")
            .setDescription(descriptionBuilder)
            .setFooter("Case #" + caseSeq)
            .build();
    }
}
