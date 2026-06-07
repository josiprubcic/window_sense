alter table physical_device_registry drop constraint chk_physical_device_registry_status;

update physical_device_registry
set status = 'CLAIMABLE'
where status = 'AVAILABLE';

alter table physical_device_registry
    add constraint chk_physical_device_registry_status check (status in ('CLAIMABLE', 'CLAIMED', 'DISABLED'));
