create table window_device_capabilities (
    window_device_id uuid not null references window_device(id) on delete cascade,
    capability varchar(40) not null,
    primary key (window_device_id, capability),
    constraint chk_window_device_capability check (
        capability in ('WINDOW_CONTROL', 'BLINDS_CONTROL', 'ENVIRONMENT_SENSOR', 'RAIN_SENSOR', 'LIGHT_SENSOR')
    )
);

create table physical_device_registry_capabilities (
    physical_device_registry_id uuid not null references physical_device_registry(id) on delete cascade,
    capability varchar(40) not null,
    primary key (physical_device_registry_id, capability),
    constraint chk_physical_device_registry_capability check (
        capability in ('WINDOW_CONTROL', 'BLINDS_CONTROL', 'ENVIRONMENT_SENSOR', 'RAIN_SENSOR', 'LIGHT_SENSOR')
    )
);

alter table physical_device_registry
    add column pairing_code_consumed_at timestamp with time zone;

insert into window_device_capabilities (window_device_id, capability)
select id, capability
from window_device
cross join (
    values
        ('WINDOW_CONTROL'),
        ('BLINDS_CONTROL'),
        ('ENVIRONMENT_SENSOR'),
        ('RAIN_SENSOR'),
        ('LIGHT_SENSOR')
) as capabilities(capability);

insert into physical_device_registry_capabilities (physical_device_registry_id, capability)
select id, 'WINDOW_CONTROL'
from physical_device_registry
where lower(coalesce(capabilities, '')) like '%window%';

insert into physical_device_registry_capabilities (physical_device_registry_id, capability)
select id, 'BLINDS_CONTROL'
from physical_device_registry
where lower(coalesce(capabilities, '')) like '%blinds%';

insert into physical_device_registry_capabilities (physical_device_registry_id, capability)
select id, 'RAIN_SENSOR'
from physical_device_registry
where lower(coalesce(capabilities, '')) like '%rain%';

insert into physical_device_registry_capabilities (physical_device_registry_id, capability)
select id, 'LIGHT_SENSOR'
from physical_device_registry
where lower(coalesce(capabilities, '')) like '%lux%'
   or lower(coalesce(capabilities, '')) like '%light%';

insert into physical_device_registry_capabilities (physical_device_registry_id, capability)
select id, 'ENVIRONMENT_SENSOR'
from physical_device_registry
where lower(coalesce(capabilities, '')) like '%temperature%'
   or lower(coalesce(capabilities, '')) like '%wind%'
   or lower(coalesce(capabilities, '')) like '%sensor%'
   or lower(coalesce(capabilities, '')) like '%environment%';

insert into physical_device_registry_capabilities (physical_device_registry_id, capability)
select registry.id, capability
from physical_device_registry registry
cross join (
    values
        ('WINDOW_CONTROL'),
        ('BLINDS_CONTROL'),
        ('ENVIRONMENT_SENSOR'),
        ('RAIN_SENSOR'),
        ('LIGHT_SENSOR')
) as capabilities(capability)
where not exists (
    select 1
    from physical_device_registry_capabilities existing
    where existing.physical_device_registry_id = registry.id
);
