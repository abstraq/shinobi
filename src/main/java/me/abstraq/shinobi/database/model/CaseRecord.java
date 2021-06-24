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

package me.abstraq.shinobi.database.model;

import java.time.Instant;

/**
 * Represents a case in the persistent storage.
 */
public record CaseRecord(long id, CaseType type, long guildID, long targetID, long moderatorID, String reason, Instant createdAt, Instant expiresAt, Long reference, boolean active) {

    /**
     * The type of action that was taken in the case.
     */
    public enum CaseType {
        WARN
    }
}
