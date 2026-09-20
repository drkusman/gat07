ALTER TABLE members ADD COLUMN position_id BIGINT REFERENCES positions(id);
