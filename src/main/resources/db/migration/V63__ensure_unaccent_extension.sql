-- V63: Ensure unaccent extension and function exist
-- This migration ensures the unaccent extension is available

-- Create unaccent extension if not exists
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Create or replace the unaccent_string wrapper function
CREATE OR REPLACE FUNCTION unaccent_string(text)
RETURNS text AS $$
BEGIN
    RETURN unaccent('unaccent', $1);
END;
$$ LANGUAGE plpgsql IMMUTABLE;
