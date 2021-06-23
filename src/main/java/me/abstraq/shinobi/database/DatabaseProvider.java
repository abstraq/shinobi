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

package me.abstraq.shinobi.database;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.abstraq.shinobi.database.model.GuildRecord;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages persistent data for the plugin by providing CRUD methods
 * for each of the data models.
 */
public class DatabaseProvider {
    private final ExecutorService executorService;
    private final Logger logger;
    private final Jdbi jdbi;

    static final String INSERT_GUILD = "INSERT INTO shinobi_guilds (guild_id) VALUES (?);";
    static final String SELECT_GUILD = "SELECT * FROM shinobi_guilds WHERE guild_id = ?;";
    static final String UPDATE_GUILD_MOD_LOG_CHANNEL = "UPDATE shinobi_guilds SET mod_log_channel_id = ? WHERE guild_id = ?;";
    static final String UPDATE_GUILD_MUTED_ROLE = "UPDATE shinobi_guilds SET muted_role_id = ? WHERE guild_id = ?;";
    static final String UPDATE_GUILD_STATUS = "UPDATE shinobi_guilds SET status = ? WHERE guild_id = ?;";
    static final String DELETE_GUILD = "DELETE FROM shinobi_guilds WHERE guild_id = ?;";

    /**
     * Constructor for DatabaseProvider.
     *
     * @param host     hostname for the PostgreSQL server.
     * @param port     port for the PostgreSQL server.
     * @param user     username for the PostgreSQL server.
     * @param password password for the PostgreSQL server.
     * @param database database name in the PostgreSQL server.
     */
    public DatabaseProvider(String host, int port, String user, String password, String database) {
        this.executorService = Executors.newCachedThreadPool();
        this.logger = LoggerFactory.getLogger(DatabaseProvider.class);

        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", host, port, database));
        dataSource.setUsername(user);
        dataSource.setPassword(password);

        this.jdbi = Jdbi.create(dataSource)
            .installPlugin(new PostgresPlugin())
            .setSqlLogger(new SqlLogger() {
                @Override
                public void logException(StatementContext context, SQLException ex) {
                    DatabaseProvider.this.logger.error("An exception occurred while executing JDBI query: {}", ex.getMessage());
                }
            })
            .registerRowMapper(GuildRecord.class, (rs, ctx) -> {
                var guildID = rs.getLong("guild_id");
                var modLogChannelID = rs.getLong("mod_log_channel_id");
                var mutedRoleID = rs.getLong("muted_role_id");
                var status = GuildRecord.GuildStatus.values()[rs.getInt("status")];
                return new GuildRecord(guildID, modLogChannelID, mutedRoleID, status);
            });
    }

    /**
     * Creates a new guild record in the database.
     *
     * @param guildID id of the guild to save.
     * @return CompletableFuture indicating the task was completed.
     */
    public CompletableFuture<Void> createGuild(long guildID) {
        return CompletableFuture.runAsync(() -> this.jdbi.useHandle(handle -> handle.createUpdate(INSERT_GUILD)
            .bind(0, guildID)
            .execute()
        ), this.executorService);
    }

    /**
     * Retrieves a guild record from the database.
     *
     * @param guildID guild id of the record to retrieve.
     * @return the record, if present. Returns null if the record doesn't exist.
     */
    public CompletableFuture<GuildRecord> retrieveGuild(long guildID) {
        return CompletableFuture.supplyAsync(() -> this.jdbi.withHandle(handle -> handle.createQuery(SELECT_GUILD)
            .bind(0, guildID)
            .mapTo(GuildRecord.class)
            .findOne()
            .orElse(null)
        ), this.executorService);
    }

    /**
     * Updates the guild record mod log channel id in the database.
     *
     * @param guildID         id of the guild to update.
     * @param modLogChannelID new mod log channel id.
     * @return CompletableFuture indicating the task was completed.
     */
    public CompletableFuture<Void> updateGuildModLogChannel(long guildID, long modLogChannelID) {
        return CompletableFuture.runAsync(() -> this.jdbi.useHandle(handle -> handle.createUpdate(UPDATE_GUILD_MOD_LOG_CHANNEL)
            .bind(0, modLogChannelID)
            .bind(1, guildID)
            .execute()
        ), this.executorService);
    }

    /**
     * Updates the guild record muted role id in the database.
     *
     * @param guildID     id of the guild to update.
     * @param mutedRoleID new muted role id.
     * @return CompletableFuture indicating the task was completed.
     */
    public CompletableFuture<Void> updateGuildMutedRoleID(long guildID, long mutedRoleID) {
        return CompletableFuture.runAsync(() -> this.jdbi.useHandle(handle -> handle.createUpdate(UPDATE_GUILD_MUTED_ROLE)
            .bind(0, mutedRoleID)
            .bind(1, guildID)
            .execute()
        ), this.executorService);
    }

    /**
     * Updates the guild record status in the database.
     *
     * @param guildID id of the guild to update.
     * @param status  new status.
     * @return CompletableFuture indicating the task was completed.
     */
    public CompletableFuture<Void> updateGuildStatus(long guildID, GuildRecord.GuildStatus status) {
        return CompletableFuture.runAsync(() -> this.jdbi.useHandle(handle -> handle.createUpdate(UPDATE_GUILD_STATUS)
            .bind(0, status.ordinal())
            .bind(1, guildID)
            .execute()
        ), this.executorService);
    }

    /**
     * Deletes guild record from the database.
     *
     * @param guildID id of the guild to delete.
     * @return CompletableFuture indicating the task was completed.
     */
    public CompletableFuture<Void> deleteGuild(long guildID) {
        return CompletableFuture.runAsync(() -> this.jdbi.useHandle(handle -> handle.createUpdate(DELETE_GUILD)
            .bind(0, guildID)
            .execute()
        ), this.executorService);
    }
}
