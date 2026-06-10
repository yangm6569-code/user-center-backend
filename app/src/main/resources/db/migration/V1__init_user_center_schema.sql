create table uc_user_account (
    id bigserial primary key,
    username varchar(80) not null unique,
    email varchar(160) unique,
    phone varchar(40) unique,
    display_name varchar(120) not null,
    password_hash varchar(120) not null,
    status varchar(24) not null,
    account_type varchar(24) not null,
    employee_no varchar(80) unique,
    factory_code varchar(80),
    department_code varchar(80),
    position_code varchar(80),
    email_verified boolean not null default false,
    failed_login_count integer not null default 0,
    locked_until timestamp with time zone,
    freeze_until timestamp with time zone,
    freeze_reason varchar(500),
    last_login_at timestamp with time zone,
    password_changed_at timestamp with time zone,
    archived_at timestamp with time zone,
    archived_by bigint,
    archive_reason varchar(500),
    deleted boolean not null default false,
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null
);

create index idx_uc_user_account_status on uc_user_account (status);
create index idx_uc_user_account_factory_department on uc_user_account (factory_code, department_code);

create table uc_access_application (
    id bigserial primary key,
    client_id varchar(120) not null unique,
    name varchar(160) not null,
    app_type varchar(24) not null,
    owner_dept varchar(120),
    status varchar(24) not null,
    client_secret_hash varchar(120),
    secret_version integer not null default 1,
    redirect_uris jsonb not null default '[]'::jsonb,
    logout_uris jsonb not null default '[]'::jsonb,
    allowed_scopes jsonb not null default '[]'::jsonb,
    access_token_ttl_seconds bigint not null default 900,
    refresh_token_ttl_seconds bigint not null default 604800,
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null
);

create table uc_role (
    id bigserial primary key,
    code varchar(120) not null,
    name varchar(160) not null,
    description varchar(500),
    scope varchar(24) not null,
    application_id bigint references uc_access_application(id),
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null
);

create unique index ux_uc_role_app_code on uc_role (coalesce(application_id, 0), code);

create table uc_group (
    id bigserial primary key,
    code varchar(120) not null unique,
    name varchar(160) not null,
    group_type varchar(24) not null,
    parent_id bigint references uc_group(id),
    path varchar(1000) not null,
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null
);

create table uc_user_group (
    id bigserial primary key,
    user_id bigint not null references uc_user_account(id),
    group_id bigint not null references uc_group(id),
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null,
    unique (user_id, group_id)
);

create table uc_user_role (
    id bigserial primary key,
    user_id bigint not null references uc_user_account(id),
    role_id bigint not null references uc_role(id),
    expires_at timestamp with time zone,
    reason varchar(500),
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null,
    unique (user_id, role_id)
);

create table uc_group_role (
    id bigserial primary key,
    group_id bigint not null references uc_group(id),
    role_id bigint not null references uc_role(id),
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null,
    unique (group_id, role_id)
);

create table uc_resource (
    id bigserial primary key,
    application_id bigint not null references uc_access_application(id),
    resource_code varchar(160) not null,
    resource_type varchar(24) not null,
    name varchar(160) not null,
    parent_id bigint references uc_resource(id),
    attributes jsonb not null default '{}'::jsonb,
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null,
    unique (application_id, resource_code)
);

create table uc_policy (
    id bigserial primary key,
    application_id bigint not null references uc_access_application(id),
    code varchar(160) not null,
    name varchar(160) not null,
    policy_type varchar(32) not null,
    rule jsonb not null default '{}'::jsonb,
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null,
    unique (application_id, code)
);

create table uc_permission (
    id bigserial primary key,
    role_id bigint not null references uc_role(id),
    resource_id bigint not null references uc_resource(id),
    scope varchar(80) not null,
    policy_id bigint references uc_policy(id),
    effect varchar(16) not null,
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null
);

create index idx_uc_permission_role on uc_permission (role_id);

create table uc_login_session (
    id bigserial primary key,
    user_id bigint not null references uc_user_account(id),
    application_id bigint references uc_access_application(id),
    refresh_token_hash varchar(128) not null unique,
    status varchar(24) not null,
    ip varchar(80),
    user_agent varchar(500),
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null
);

create index idx_uc_login_session_user_status on uc_login_session (user_id, status);

create table uc_mfa_credential (
    id bigserial primary key,
    user_id bigint not null references uc_user_account(id),
    mfa_type varchar(24) not null,
    label varchar(120) not null,
    secret_ref varchar(300) not null,
    enabled boolean not null default true,
    last_used_at timestamp with time zone,
    create_time timestamp with time zone not null,
    update_time timestamp with time zone not null
);

create table uc_audit_event (
    id bigserial primary key,
    category varchar(32) not null,
    event_type varchar(120) not null,
    actor_id bigint,
    target_id bigint,
    client_id varchar(120),
    ip varchar(80),
    user_agent varchar(500),
    result varchar(24) not null,
    reason varchar(500),
    trace_id varchar(80) not null,
    payload jsonb not null default '{}'::jsonb,
    create_time timestamp with time zone not null
);

create index idx_uc_audit_event_category_created on uc_audit_event (category, create_time desc);
create index idx_uc_audit_event_actor_created on uc_audit_event (actor_id, create_time desc);
create index idx_uc_audit_event_target_created on uc_audit_event (target_id, create_time desc);
