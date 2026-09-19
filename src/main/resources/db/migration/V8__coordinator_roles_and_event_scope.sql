ALTER TABLE members DROP CONSTRAINT ck_member_role;
ALTER TABLE members ADD CONSTRAINT ck_member_role CHECK (role IN (
    'MEMBER', 'POLLING_UNIT_COORDINATOR', 'WARD_COORDINATOR', 'LGA_COORDINATOR',
    'COORDINATOR', 'ZONAL_COORDINATOR', 'GRAND_PATRON', 'ADMIN'
));

ALTER TABLE events ADD COLUMN zone_id BIGINT REFERENCES zones(id);
ALTER TABLE events ADD COLUMN state_id BIGINT REFERENCES states(id);
CREATE INDEX idx_events_zone ON events(zone_id);
CREATE INDEX idx_events_state ON events(state_id);

ALTER TABLE members ADD COLUMN pending_role VARCHAR(40);
ALTER TABLE members ADD COLUMN pending_role_requested_by BIGINT REFERENCES members(id);
ALTER TABLE members ADD COLUMN pending_role_stage VARCHAR(20);
ALTER TABLE members ADD COLUMN pending_role_zonal_approved_by BIGINT REFERENCES members(id);
