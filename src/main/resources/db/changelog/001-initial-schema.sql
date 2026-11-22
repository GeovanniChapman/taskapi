--liquibase formatted sql

--changeset geovanni:001-create-users
CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email)
);

--changeset geovanni:002-create-tasks
CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    completed BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_tasks_owner
        FOREIGN KEY (owner_id) REFERENCES app_users(id)
);

CREATE INDEX idx_tasks_owner_id_id ON tasks(owner_id, id);