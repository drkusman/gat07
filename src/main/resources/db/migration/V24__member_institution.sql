ALTER TABLE members ADD COLUMN institution_id BIGINT REFERENCES institutions(id);
