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

package me.abstraq.shinobi.database;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import me.abstraq.shinobi.database.model.CaseRecord;
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

    static final String INSERT_GUILD = "INSERT INTO shinobi_guilds (guild_id) VALUES (?);";
    static final String SELECT_GUILD = "SELECT * FROM shinobi_guilds WHERE guild_id = ?;";
    static final String UPDATE_GUILD_MOD_LOG_CHANNEL = "UPDATE shinobi_guilds SET mod_log_channel_id = ? WHERE guild_id = ?;";
    static final String UPDATE_GUILD_MUTED_ROLE = "UPDATE shinobi_guilds SET muted_role_id = ? WHERE guild_id = ?;";
    static final String UPDATE_GUILD_STATUS = "UPDATE shinobi_guilds SET status = ? WHERE guild_id = ?;";
    static final String DELETE_GUILD = "DELETE FROM shinobi_guilds WHERE guild_id = ?;";

    static final String INSERT_CASE = "INSERT INTO shinobi_cases (case_type, guild_id, target_id, moderator_id, reason, created_at, expires_at, reference) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
    static final String SELECT_CASE_BY_GUILD_SEQ = "SELECT * FROM (SELECT *, row_number() OVER (PARTITION BY guild_id ORDER BY id) row_num FROM shinobi_cases) AS guild_cases WHERE guild_id = ? AND row_num = ?;";
    static final String SELECT_CASES_BY_TARGET = "SELECT * FROM shinobi_cases WHERE guild_id = ? AND target_id = ?;";
    static final String SELECT_SEQ_OF_CASE_IN_GUILD = "SELECT row_num FROM (SELECT id, row_number() OVER (PARTITION BY guild_id ORDER BY id) row_num FROM shinobi_cases) AS guild_cases WHERE id = ?;";

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
            })
            .registerRowMapper(CaseRecord.class, (rs, ctx) -> {
                var id = rs.getLong("id");
                var caseType = CaseRecord.CaseType.values()[rs.getInt("case_type")];
                var guildID = rs.getLong("guild_id");
                var targetID = rs.getLong("target_id");
                var moderatorID = rs.getLong("moderator_id");
                var reason = rs.getString("reason");
                var createdAt = rs.getTimestamp("created_at").toInstant();
                var expiresAt = rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toInstant() : null;
                var reference = rs.getLong("reference");
                var active = rs.getBoolean("active");
                return new CaseRecord(id, caseType, guildID, targetID, moderatorID, reason, createdAt, expiresAt, reference, active);
            });
    }

    /**
     * Creates a new guild record in the database.
     *
     * @param guildID id of the guild to save.
     */
    public void createGuild(long guildID) {
        this.jdbi.useHandle(handle -> handle.createUpdate(INSERT_GUILD)
            .bind(0, guildID)
            .execute()
        );
    }

    /**
     * Retrieves a guild record from the database.
     *
     * @param guildID guild id of the record to retrieve.
     * @return the record, if present. Returns null if the record doesn't exist.
     */
    public GuildRecord retrieveGuild(long guildID) {
        return this.jdbi.withHandle(handle -> handle.createQuery(SELECT_GUILD)
            .bind(0, guildID)
            .mapTo(GuildRecord.class)
            .findOne()
            .orElse(null)
        );
    }

    /**
     * Updates the guild record mod log channel id in the database.
     *
     * @param guildID         id of the guild to update.
     * @param modLogChannelID new mod log channel id.
     */
    public void updateGuildModLogChannel(long guildID, long modLogChannelID) {
        this.jdbi.useHandle(handle -> handle.createUpdate(UPDATE_GUILD_MOD_LOG_CHANNEL)
            .bind(0, modLogChannelID)
            .bind(1, guildID)
            .execute()
        );
    }

    /**
     * Updates the guild record muted role id in the database.
     *
     * @param guildID     id of the guild to update.
     * @param mutedRoleID new muted role id.
     */
    public void updateGuildMutedRoleID(long guildID, long mutedRoleID) {
        this.jdbi.useHandle(handle -> handle.createUpdate(UPDATE_GUILD_MUTED_ROLE)
            .bind(0, mutedRoleID)
            .bind(1, guildID)
            .execute()
        );
    }

    /**
     * Updates the guild record status in the database.
     *
     * @param guildID id of the guild to update.
     * @param status  new status.
     */
    public void updateGuildStatus(long guildID, GuildRecord.GuildStatus status) {
        this.jdbi.useHandle(handle -> handle.createUpdate(UPDATE_GUILD_STATUS)
            .bind(0, status.ordinal())
            .bind(1, guildID)
            .execute()
        );
    }

    /**
     * Deletes guild record from the database.
     *
     * @param guildID id of the guild to delete.
     */
    public void deleteGuild(long guildID) {
        this.jdbi.useHandle(handle -> handle.createUpdate(DELETE_GUILD)
            .bind(0, guildID)
            .execute()
        );
    }

    /**
     * Creates a new case record in the database.
     *
     * @param guildID id of the guild to save.
     * @return the sequence number of the case in the guild.
     */
    public CaseRecord createCase(CaseRecord.CaseType type, long guildID, long targetID, long moderatorID, String reason, Instant createdAt, Instant expiresAt, Long reference) {
        var caseID = this.jdbi.withHandle(handle -> handle.createUpdate(INSERT_CASE)
            .bind(0, type.ordinal())
            .bind(1, guildID)
            .bind(2, targetID)
            .bind(3, moderatorID)
            .bind(4, reason)
            .bind(5, createdAt)
            .bind(6, expiresAt)
            .bind(7, reference)
            .executeAndReturnGeneratedKeys("id")
            .mapTo(Long.class)
            .one()
        );
        var caseSeq = this.retrieveGuildSeqForCase(caseID);
        return new CaseRecord(caseSeq, type, guildID, targetID, moderatorID, reason, createdAt, expiresAt, reference, true);
    }

    /**
     * Retrieves a case record by its sequence number in a guild.
     *
     * @param guildID  id of the guild the case is in.
     * @param guildSeq sequence number of the case in the guild.
     * @return the record, if present. Returns null if the record doesn't exist.
     */
    public CaseRecord retrieveCaseBySeq(long guildID, long guildSeq) {
        return this.jdbi.withHandle(handle -> handle.createQuery(SELECT_CASE_BY_GUILD_SEQ)
            .bind(0, guildID)
            .bind(1, guildSeq)
            .mapTo(CaseRecord.class)
            .findOne()
            .orElse(null)
        );
    }

    /**
     * Retrieves a list of cases where the target is the specified user.
     *
     * @param guildID  id of the guild the cases are in.
     * @param targetID id of the user to filter cases by.
     * @return the list of cases. Returns an empty list if no cases exist.
     */
    public List<CaseRecord> retrieveCasesByTarget(long guildID, long targetID) {
        return this.jdbi.withHandle(handle -> handle.createQuery(SELECT_CASES_BY_TARGET)
            .bind(0, guildID)
            .bind(1, targetID)
            .mapTo(CaseRecord.class)
            .list()
        );
    }

    /**
     * Retrieves the sequential number of the case in its guild.
     *
     * @param caseID id of the case.
     * @return the sequence number, if present. Returns null if the record doesn't exist.
     */
    public long retrieveGuildSeqForCase(long caseID) {
        return this.jdbi.withHandle(handle -> handle.createQuery(SELECT_SEQ_OF_CASE_IN_GUILD)
            .bind(0, caseID)
            .mapTo(Long.class)
            .findOne()
            .orElse(null)
        );
    }
}
