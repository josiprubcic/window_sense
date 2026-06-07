alter table physical_device_registry add column thingsboard_access_token_hash varchar(64);

create unique index ux_physical_device_registry_tb_access_token_hash
    on physical_device_registry (thingsboard_access_token_hash);
