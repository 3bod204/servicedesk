CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    avatar_url    VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

CREATE TABLE roles (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX ix_user_roles_role ON user_roles (role_id);


CREATE TABLE queues (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    queue_id   BIGINT       NOT NULL REFERENCES queues (id),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_categories_name_queue UNIQUE (name, queue_id)
);

CREATE INDEX ix_categories_queue ON categories (queue_id);

CREATE TABLE queue_members (
    queue_id BIGINT NOT NULL REFERENCES queues (id) ON DELETE CASCADE,
    user_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (queue_id, user_id)
);

CREATE INDEX ix_queue_members_user ON queue_members (user_id);


CREATE TABLE sla_policies (
    id                     BIGSERIAL PRIMARY KEY,
    priority               VARCHAR(20) NOT NULL UNIQUE,
    first_response_minutes INTEGER     NOT NULL,
    resolution_minutes     INTEGER     NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_sla_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_sla_positive
        CHECK (first_response_minutes > 0 AND resolution_minutes > 0)
);

CREATE SEQUENCE ticket_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE tickets (
    id           BIGSERIAL PRIMARY KEY,
    reference    VARCHAR(20)  NOT NULL UNIQUE,
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    priority     VARCHAR(20)  NOT NULL,
    category_id  BIGINT       NOT NULL REFERENCES categories (id),
    queue_id     BIGINT       NOT NULL REFERENCES queues (id),
    requester_id BIGINT       NOT NULL REFERENCES users (id),
    assignee_id  BIGINT       REFERENCES users (id),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    first_response_at TIMESTAMPTZ,
    resolved_at       TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,

    first_response_due_at TIMESTAMPTZ,
    sla_due_at            TIMESTAMPTZ,
    pending_since         TIMESTAMPTZ,
    pending_minutes_total INTEGER     NOT NULL DEFAULT 0,
    first_response_breached BOOLEAN   NOT NULL DEFAULT FALSE,
    resolution_breached     BOOLEAN   NOT NULL DEFAULT FALSE,
    breach_warning_sent     BOOLEAN   NOT NULL DEFAULT FALSE,

    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT  NOT NULL DEFAULT 0,

    CONSTRAINT ck_ticket_status CHECK (status IN (
        'NEW', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_REQUESTER',
        'RESOLVED', 'CLOSED', 'REOPENED')),
    CONSTRAINT ck_ticket_priority CHECK (priority IN (
        'LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE INDEX ix_tickets_status      ON tickets (status);
CREATE INDEX ix_tickets_assignee    ON tickets (assignee_id);
CREATE INDEX ix_tickets_requester   ON tickets (requester_id);
CREATE INDEX ix_tickets_created_at  ON tickets (created_at DESC);

CREATE INDEX ix_tickets_queue    ON tickets (queue_id);
CREATE INDEX ix_tickets_category ON tickets (category_id);
CREATE INDEX ix_tickets_priority ON tickets (priority);
CREATE INDEX ix_tickets_sla_due  ON tickets (sla_due_at)
    WHERE deleted = FALSE AND resolved_at IS NULL;

CREATE INDEX ix_tickets_search ON tickets
    USING GIN (to_tsvector('english', title || ' ' || description));


CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id),
    parent_id  BIGINT      REFERENCES comments (id),
    author_id  BIGINT      NOT NULL REFERENCES users (id),
    body       TEXT        NOT NULL,
    internal   BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_comments_ticket ON comments (ticket_id, created_at);
CREATE INDEX ix_comments_parent ON comments (parent_id);


CREATE TABLE attachments (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL REFERENCES tickets (id),
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(500) NOT NULL UNIQUE,
    uploaded_by  BIGINT       NOT NULL REFERENCES users (id),
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);

CREATE INDEX ix_attachments_ticket ON attachments (ticket_id);

CREATE TABLE audit_entries (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id),
    actor_id   BIGINT      NOT NULL REFERENCES users (id),
    field      VARCHAR(60) NOT NULL,
    old_value  TEXT,
    new_value  TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_audit_ticket ON audit_entries (ticket_id, created_at DESC);
CREATE INDEX ix_audit_actor  ON audit_entries (actor_id);


CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_refresh_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_expires ON refresh_tokens (expires_at);


CREATE TABLE login_attempts (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,
    successful   BOOLEAN      NOT NULL,
    ip_address   VARCHAR(45),
    attempted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_login_attempts_email_time
    ON login_attempts (LOWER(email), attempted_at DESC);


CREATE TABLE notifications (
    id            BIGSERIAL PRIMARY KEY,
    ticket_id     BIGINT       REFERENCES tickets (id),
    recipient     VARCHAR(255) NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    body          TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    TEXT,
    next_retry_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at       TIMESTAMPTZ,
    CONSTRAINT ck_notification_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX ix_notifications_pending ON notifications (status, next_retry_at)
    WHERE status = 'PENDING';


CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);