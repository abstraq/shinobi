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

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Handles sending button prompts with messages.
 */
public class PromptService extends ListenerAdapter {
    private final HashMap<UUID, CompletableFuture<InteractionHook>> futures;

    public PromptService() {
        this.futures = new HashMap<>();
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event) {
        event.deferEdit().queue();
        try {
            UUID buttonIdentifier = UUID.fromString(event.getComponentId());
            CompletableFuture<InteractionHook> future = this.futures.get(buttonIdentifier);
            if (future == null) {
                return;
            }
            future.complete(event.getHook());
        } catch (IllegalArgumentException ignored) {
            // Button is not a registered prompt.
        }
    }

    /**
     * Creates a prompt that can be used to run an action when the button is pressed.
     *
     * @return a {@link Pair} containing the prompt id on the left and a future that will be completed when the button is pressed on the right.
     */
    public Pair<UUID, CompletableFuture<InteractionHook>> createPrompt() {
        UUID uuid = UUID.randomUUID();
        CompletableFuture<InteractionHook> future = new CompletableFuture<>();
        this.futures.put(uuid, future);
        return ImmutablePair.of(uuid, future);
    }

    /**
     * Checks if the prompt service contains a specific prompt.
     *
     * @param promptID the id of the prompt to check for.
     * @return true if the service contains the prompt, false otherwise.
     */
    public boolean containsPrompt(UUID promptID) {
        return this.futures.containsKey(promptID);
    }

    /**
     * Deletes a prompt from the service.
     *
     * @param promptID the id of the prompt to check for.
     */
    public void deletePrompt(UUID promptID) {
        this.futures.remove(promptID);
    }
}
