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

package me.abstraq.shinobi.database.model;

/**
 * Represents a guild in the persistent storage.
 */
public record GuildRecord(long id, Long modLogChannelID, Long mutedRoleID, GuildStatus status) {

    /**
     * Statuses that the guild may have.
     */
    public enum GuildStatus {
        /**
         * Shinobi can be used in this guild.
         */
        ACTIVE,

        /**
         * This guiled is barred from using any of Shinobi's features.
         */
        DISABLED
    }
}
