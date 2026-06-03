create table physical_device_registry (
    id uuid primary key,
    serial_number varchar(128) not null unique,
    pairing_code_hash varchar(64) not null unique,
    tb_device_id varchar(128) not null,
    status varchar(20) not null,
    claimed_by_user_id uuid,
    claimed_room_id uuid,
    created_at timestamp with time zone not null,
    claimed_at timestamp with time zone,
    last_seen_at timestamp with time zone,
    constraint chk_physical_device_registry_status check (status in ('AVAILABLE', 'CLAIMED', 'DISABLED'))
);

create index idx_physical_device_registry_pairing_code_hash on physical_device_registry(pairing_code_hash);
create index idx_physical_device_registry_claimed_by_user_id on physical_device_registry(claimed_by_user_id);
create index idx_physical_device_registry_claimed_room_id on physical_device_registry(claimed_room_id);
