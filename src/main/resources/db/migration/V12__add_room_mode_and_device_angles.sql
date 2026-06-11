alter table room
    add column manual_mode boolean not null default false;

alter table window_device
    add column desired_angle_day double precision not null default 90;

alter table window_device
    add column desired_angle_night double precision not null default 0;

alter table window_device
    add column desired_angle_rain double precision not null default 15;
