-- Create unaccent_string function wrapper for Flyway compatibility
CREATE OR REPLACE FUNCTION unaccent_string(text)
RETURNS text AS $$
BEGIN
    RETURN unaccent('unaccent', $1);
END;
$$ LANGUAGE plpgsql IMMUTABLE;
