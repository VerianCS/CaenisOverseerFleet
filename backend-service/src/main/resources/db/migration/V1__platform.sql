CREATE TABLE IF NOT EXISTS mining_events (
 id bigserial PRIMARY KEY, player_id uuid NOT NULL, player_name varchar(32) NOT NULL,
 world varchar(64) NOT NULL, x integer NOT NULL, y integer NOT NULL, z integer NOT NULL,
 block_type varchar(64) NOT NULL, tool_used varchar(64), tool_efficiency_level integer NOT NULL DEFAULT 0,
 has_haste boolean NOT NULL DEFAULT false, has_mining_fatigue boolean NOT NULL DEFAULT false,
 is_exposed boolean NOT NULL DEFAULT false, break_delta_ms integer, created_at timestamptz NOT NULL
);
ALTER TABLE mining_events ADD COLUMN IF NOT EXISTS instance_id varchar(64) NOT NULL DEFAULT 'legacy';
ALTER TABLE mining_events ADD COLUMN IF NOT EXISTS event_id varchar(36);
ALTER TABLE mining_events ADD COLUMN IF NOT EXISTS topology_known boolean NOT NULL DEFAULT true;
ALTER TABLE mining_events ADD COLUMN IF NOT EXISTS raw_event jsonb;
ALTER TABLE mining_events ADD COLUMN IF NOT EXISTS position geometry(Point, 0)
 GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(x, z), 0)) STORED;
CREATE UNIQUE INDEX IF NOT EXISTS mining_event_identity ON mining_events(event_id);
CREATE INDEX IF NOT EXISTS mining_spatial ON mining_events USING gist(position);
CREATE INDEX IF NOT EXISTS mining_scope ON mining_events(instance_id, world, created_at DESC);
CREATE INDEX IF NOT EXISTS mining_player_history ON mining_events(player_id, instance_id, world, created_at DESC);

CREATE TABLE IF NOT EXISTS anomaly_alerts (
 id bigserial PRIMARY KEY, player_id uuid NOT NULL, player_name varchar(32) NOT NULL,
 alert_type varchar(32) NOT NULL, severity varchar(16) NOT NULL, diagnostic_data text NOT NULL,
 world varchar(64) NOT NULL, x integer NOT NULL, y integer NOT NULL, z integer NOT NULL,
 created_at timestamptz NOT NULL
);
ALTER TABLE anomaly_alerts ADD COLUMN IF NOT EXISTS instance_id varchar(64) NOT NULL DEFAULT 'legacy';
CREATE INDEX IF NOT EXISTS alerts_scope ON anomaly_alerts(instance_id, created_at DESC);
CREATE INDEX IF NOT EXISTS alerts_player ON anomaly_alerts(player_id, created_at DESC);

CREATE TABLE accounts (
 id uuid PRIMARY KEY, username varchar(64) UNIQUE NOT NULL, password_hash text NOT NULL,
 role varchar(16) NOT NULL CHECK (role IN ('SUPERADMIN','MODERATOR','ANALYST')),
 enabled boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE sessions (
 id uuid PRIMARY KEY, account_id uuid NOT NULL REFERENCES accounts(id),
 expires_at timestamptz NOT NULL
);
CREATE INDEX sessions_expiry ON sessions(expires_at);

CREATE TABLE instances (
 id varchar(64) PRIMARY KEY, name varchar(100) NOT NULL, cluster_group varchar(64) NOT NULL,
 region varchar(64) NOT NULL, enabled boolean NOT NULL DEFAULT true,
 agent_secret text NOT NULL, agent_token_hash text, agent_token_expires_at timestamptz,
 rcon_host varchar(253), rcon_port integer NOT NULL DEFAULT 25575, rcon_password text,
 vault_path varchar(200), manager_url text, manager_key text,
 last_seen timestamptz, health jsonb, configuration jsonb NOT NULL DEFAULT '{}',
 automation_enabled boolean NOT NULL DEFAULT false, automation_action varchar(10) NOT NULL DEFAULT 'kick',
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE health_samples (
 id bigserial PRIMARY KEY, instance_id varchar(64) NOT NULL REFERENCES instances(id),
 recorded_at timestamptz NOT NULL DEFAULT now(), tps double precision NOT NULL,
 mspt double precision NOT NULL, heap_used bigint NOT NULL, heap_max bigint NOT NULL, players integer NOT NULL
);
CREATE INDEX health_history ON health_samples(instance_id, recorded_at DESC);
CREATE TABLE ingress_receipts (
 instance_id varchar(64) NOT NULL REFERENCES instances(id), nonce uuid NOT NULL,
 purpose varchar(16) NOT NULL, body_hash text NOT NULL, received_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(instance_id, nonce)
);
CREATE TABLE audit_events (
 id bigserial PRIMARY KEY, correlation_id uuid NOT NULL, actor varchar(100) NOT NULL,
 instance_id varchar(64), action varchar(40) NOT NULL, command text NOT NULL,
 outcome varchar(24) NOT NULL, detail text NOT NULL DEFAULT '', created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_timeline ON audit_events(created_at DESC, id DESC);
CREATE INDEX audit_correlation ON audit_events(correlation_id);
CREATE FUNCTION forbid_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Audit events are append-only'; END $$;
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE OR TRUNCATE ON audit_events
 FOR EACH STATEMENT EXECUTE FUNCTION forbid_audit_mutation();

CREATE TABLE intervention_limits (
 instance_id varchar(64) NOT NULL, player_id uuid NOT NULL, last_action timestamptz NOT NULL,
 PRIMARY KEY(instance_id, player_id)
);
CREATE TABLE baseline_models (
 instance_id varchar(64) NOT NULL, player_id uuid NOT NULL, world varchar(64) NOT NULL,
 samples bigint NOT NULL, occluded bigint NOT NULL, trained_at timestamptz NOT NULL,
 PRIMARY KEY(instance_id, player_id, world)
);
