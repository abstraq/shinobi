CREATE TABLE IF NOT EXISTS shinobi_guilds
(
    guild_id BIGINT PRIMARY KEY NOT NULL,
    mod_log_channel_id BIGINT,
    muted_role_id BIGINT,
    status   SMALLINT DEFAULT 0
);

CREATE INDEX guild_id_index ON shinobi_guilds (guild_id);

CREATE TABLE IF NOT EXISTS shinobi_cases
(
    id           BIGSERIAL PRIMARY KEY,
    case_type    SMALLINT  NOT NULL,
    guild_id     BIGINT    NOT NULL,
    target_id    BIGINT    NOT NULL,
    moderator_id BIGINT    NOT NULL,
    reason       VARCHAR(255),
    created_at   TIMESTAMP NOT NULL,
    expires_at   TIMESTAMP,
    reference    BIGINT,
    active       BOOLEAN DEFAULT TRUE
);

CREATE INDEX case_guild_index ON shinobi_cases (guild_id, target_id, active);