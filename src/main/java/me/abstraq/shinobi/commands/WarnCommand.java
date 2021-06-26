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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import net.dv8tion.jda.api.interactions.components.Button;
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
    private final Logger logger;

    WarnCommand(Shinobi client) {
        this.client = client;
        this.logger = LoggerFactory.getLogger(WarnCommand.class);
    }

    @Override
    public void execute(SlashCommandEvent event, GuildRecord guildRecord, Guild guild, TextChannel channel, Member sender) {
        DatabaseProvider database = this.client.databaseProvider();
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

        OptionMapping reasonOption = event.getOption("reason");
        String reason = reasonOption != null ? reasonOption.getAsString() : null;

        // Make sure the reason isn't over 255 characters.
        if (reason != null && reason.length() > 255) {
            event.reply("Your reason must be a max of 255 characters.")
                .setEphemeral(true)
                .queue();
            return;
        }

        OptionMapping referenceOption = event.getOption("reference");
        Long reference = referenceOption != null ? referenceOption.getAsLong() : null;

        // Check if the case referenced actually exists.
        if (reference != null) {
            var referenceCase = database.retrieveCaseBySeq(guild.getIdLong(), reference);
            if (referenceCase == null) {
                event.reply("The case '" + reference + "' that you tried to reference does not exist.")
                    .setEphemeral(true)
                    .queue();
                return;
            }
        }

        var guildID = guild.getIdLong();
        var targetID = target.getIdLong();
        var moderatorID = sender.getIdLong();
        var createdAt = Instant.now();

        var cancelPrompt = this.client.promptService().createPrompt();
        var cancelPromptID = cancelPrompt.getLeft();
        var cancelPromptFuture = cancelPrompt.getRight();

        var confirmPrompt = this.client.promptService().createPrompt();
        var confirmPromptID = confirmPrompt.getLeft();
        var confirmPromptFuture = confirmPrompt.getRight();

        cancelPromptFuture.thenAccept(hook -> {
            hook.editOriginal("Cancelled warning action.")
                .setActionRows(Collections.emptyList())
                .queue();
            this.client.promptService().deletePrompt(cancelPromptID);
            this.client.promptService().deletePrompt(confirmPromptID);
        });

        confirmPromptFuture.thenAccept(hook -> {
            // Create the case record.
            var caseSeq = database.createCase(CaseRecord.CaseType.WARN, guildID, targetID, moderatorID, reason, createdAt, null, reference);

            hook.editOriginalFormat("Successfully warned %s.\nCase #%s", target.getUser().getAsTag(), caseSeq)
                .setActionRows(Collections.emptyList())
                .queue();
            this.client.promptService().deletePrompt(cancelPromptID);
            this.client.promptService().deletePrompt(confirmPromptID);

            // Sends a notification to the target's inbox.
            target.getUser().openPrivateChannel().queue(dm -> {
                if (dm != null) {
                    dm.sendMessageEmbeds(this.inboxMessage(caseSeq, guild, sender, reason, createdAt)).queue();
                }
            });

            OptionMapping silentOption = event.getOption("silent");
            boolean silent = silentOption != null && silentOption.getAsBoolean();

            // Notify the channel of the action.
            if (!silent) {
                // TODO: Looks like a reply to a deleted message. Maybe send a channel message instead of using the hook?
                hook.sendMessageEmbeds(this.channelMessage(caseSeq, target, sender, reason, createdAt, reference)).queue();
            }
        });

        List<CaseRecord> previousCases = database.retrieveCasesByTarget(guildID, targetID);
        int warns = 0;
        // Counts the amount of each case that target has.
        for (var caseRecord : previousCases) {
            if (caseRecord.type() == CaseRecord.CaseType.WARN) {
                warns++;
            }
        }

        var targetTag = target.getUser().getAsTag();
        MessageEmbed embed = new EmbedBuilder()
            .setAuthor(String.format("Are you sure you want to warn %s (%s)?", targetTag, targetID), null, target.getUser().getEffectiveAvatarUrl())
            .setColor(13091884)
            .setDescription("This action will warn **" + targetTag + "**" + (reason != null ? " for **" + reason + "**" : "") + (reference != null ? " referencing case #**" + reference + "**." : "."))
            .setFooter(warns + " warns.")
            .build();

        event.replyEmbeds(embed)
            .addActionRow(Button.secondary(cancelPromptID.toString(), "Cancel"), Button.danger(confirmPromptID.toString(), "Warn"))
            .setEphemeral(true)
            .queue();

        // Time out if no action taken in 15 seconds.
        this.client.schedulerService().schedule(() -> {
            if (this.client.promptService().containsPrompt(cancelPromptID)) {
                event.getHook().editOriginal("Action timed out.")
                    .setActionRows(Collections.emptyList())
                    .queue();

                this.client.promptService().deletePrompt(cancelPromptID);
                this.client.promptService().deletePrompt(confirmPromptID);
            }
        }, 15L, TimeUnit.SECONDS);
    }

    private MessageEmbed inboxMessage(long caseSeq, Guild guild, Member moderator, String reason, Instant createdAt) {
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

    private MessageEmbed channelMessage(long caseSeq, Member target, Member moderator, String reason, Instant createdAt, Long reference) {
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
