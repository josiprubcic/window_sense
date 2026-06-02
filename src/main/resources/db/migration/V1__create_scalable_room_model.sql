create table app_user (
    id uuid primary key,
    auth0_sub varchar(255) not null unique,
    email varchar(320) not null,
    display_name varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table home (
    id uuid primary key,
    app_user_id uuid not null references app_user(id),
    name varchar(120) not null,
    tb_customer_id varchar(128),
    tb_asset_id varchar(128),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_home_user_name unique (app_user_id, name)
);

create table room (
    id uuid primary key,
    home_id uuid not null references home(id),
    name varchar(120) not null,
    tb_asset_id varchar(128) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_room_home_name unique (home_id, name)
);

create table window_device (
    id uuid primary key,
    room_id uuid not null references room(id),
    name varchar(120) not null,
    device_type varchar(20) not null,
    is_virtual boolean not null,
    status varchar(20) not null,
    tb_device_id varchar(128) not null,
    physical_hardware_id varchar(128) unique,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_window_device_room_name unique (room_id, name),
    constraint chk_window_device_type check (device_type in ('PHYSICAL', 'VIRTUAL')),
    constraint chk_window_device_status check (status in ('ACTIVE', 'INACTIVE', 'ERROR')),
    constraint chk_window_device_virtual_consistency check (
        (device_type = 'VIRTUAL' and is_virtual = true)
        or (device_type = 'PHYSICAL' and is_virtual = false)
    )
);

create index idx_home_app_user_id on home(app_user_id);
create index idx_room_home_id on room(home_id);
create index idx_window_device_room_id on window_device(room_id);
