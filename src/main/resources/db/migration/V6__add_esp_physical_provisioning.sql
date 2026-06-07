alter table physical_device_registry add column hardware_id varchar(128);
alter table physical_device_registry add column firmware_version varchar(64);
alter table physical_device_registry add column capabilities text;
alter table physical_device_registry add column device_secret_hash varchar(64);
alter table physical_device_registry add column provisioning_session_hash varchar(64);
alter table physical_device_registry add column provisioning_session_expires_at timestamp with time zone;
alter table physical_device_registry add column bootstrapped_at timestamp with time zone;

create unique index ux_physical_device_registry_hardware_id
    on physical_device_registry (hardware_id);

create unique index ux_physical_device_registry_provisioning_session_hash
    on physical_device_registry (provisioning_session_hash);
