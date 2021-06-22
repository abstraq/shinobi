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
    private final Logger logger;
    private final Jdbi jdbi;

    static final String INSERT_GUILD = "INSERT INTO shinobi_guilds VALUES (?, ?, ?, ?);";
    static final String SELECT_GUILD = "SELECT * FROM shinobi_guilds WHERE guild_id = ?;";

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
     * Saves a guild record to the database.
     *
     * @param guildRecord record of the guild to save.
     * @return CompletableFuture indicating the task was completed.
     */
    public CompletableFuture<Void> createGuild(GuildRecord guildRecord) {
        return CompletableFuture.runAsync(() -> this.jdbi.useHandle(handle -> handle.createUpdate(INSERT_GUILD)
            .bind(0, guildRecord.id())
            .bind(1, guildRecord.modLogChannelID())
            .bind(2, guildRecord.mutedRoleID())
            .bind(3, guildRecord.status().ordinal())
            .execute()
        ));
    }

    /**
     * Retrieves a guild record from the database, returns Optional.empty() if the requested record does not exist.
     *
     * @param guildID guild id of the record to retrieve.
     * @return the record, if present.
     */
    public CompletableFuture<Optional<GuildRecord>> retrieveGuild(long guildID) {
        return CompletableFuture.supplyAsync(() -> this.jdbi.withHandle(handle -> handle.createQuery(SELECT_GUILD)
            .bind(0, guildID)
            .mapTo(GuildRecord.class)
            .findOne()
        ));
    }
}
