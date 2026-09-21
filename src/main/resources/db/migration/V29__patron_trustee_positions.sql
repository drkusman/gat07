INSERT INTO positions (title, committee_id)
SELECT title, c.id
FROM committees c
CROSS JOIN (
    SELECT 'Patron' AS title
    UNION ALL SELECT 'Grand Patron'
    UNION ALL SELECT 'Board of Trustees Member'
) t
WHERE c.code = 'BOT'
  AND NOT EXISTS (
    SELECT 1 FROM positions p WHERE p.committee_id = c.id AND LOWER(p.title) = LOWER(t.title)
  );
