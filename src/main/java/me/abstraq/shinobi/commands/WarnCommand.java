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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import me.abstraq.shinobi.Shinobi;
import me.abstraq.shinobi.database.DatabaseProvider;
import me.abstraq.shinobi.database.model.CaseRecord;
import me.abstraq.shinobi.database.model.GuildRecord;
import me.abstraq.shinobi.services.NotificationService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
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
        Long referenceID = referenceOption != null ? referenceOption.getAsLong() : null;

        // Check if the case referenced actually exists.
        if (referenceID != null) {
            var referenceCase = database.retrieveCaseBySeq(guild.getIdLong(), referenceID);
            if (referenceCase == null) {
                event.replyFormat("The case '%s' that you tried to reference does not exist.", referenceID)
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
            var caseRecord = database.createCase(CaseRecord.CaseType.WARN, guildID, targetID, moderatorID, reason, createdAt, null, referenceID);

            // Notify the target user in dms.
            target.getUser().openPrivateChannel().queue(privateChannel -> {
                if (privateChannel != null) {
                    NotificationService.publishPrivateMessage(caseRecord, privateChannel, guild, sender);
                }
            });

            // Notify the mod log channel if it exists.
            if (guildRecord.modLogChannelID() != null) {
                TextChannel modLogChannel = guild.getTextChannelById(guildRecord.modLogChannelID());
                if (modLogChannel != null) {
                    boolean canEmbed = modLogChannel.canTalk() && guild.getSelfMember().hasPermission(modLogChannel, Permission.MESSAGE_EMBED_LINKS);
                    if (canEmbed) {
                        NotificationService.publishModLog(caseRecord, modLogChannel, target, sender);
                    }
                }
            }

            // Notify the current channel if notifications aren't suppressed.
            OptionMapping silentOption = event.getOption("silent");
            boolean silent = silentOption != null && silentOption.getAsBoolean();
            boolean canEmbed = channel.canTalk() && guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS);
            if (!silent && canEmbed) {
                NotificationService.publishChannelBroadcast(caseRecord, channel, target, sender);
            }

            // Send moderator a success notification.
            var successNotification = new StringBuilder("Successfully warned ")
                .append(target.getUser().getAsTag())
                .append(" [Case #")
                .append(caseRecord.id())
                .append("].");

            if (!canEmbed) {
                successNotification.append("\nCould not notify current channel because I can not send embeds here.");
            }
            hook.editOriginal(successNotification.toString())
                .setActionRows(Collections.emptyList())
                .queue();
            this.client.promptService().deletePrompt(cancelPromptID);
            this.client.promptService().deletePrompt(confirmPromptID);
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
            .setDescription("This action will warn **" + targetTag + "**" + (reason != null ? " for **" + reason + "**" : "") + (referenceID != null ? " referencing case #**" + referenceID + "**." : "."))
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
}
