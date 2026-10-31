-- Postgame of a completed run: the user beat the game (credits, status COMPLETADO) but keeps playing it.
-- postgame is only true on COMPLETADO runs; postgame_end_date records when the postgame ended (it clears the flag).
ALTER TABLE experiences ADD COLUMN postgame BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE experiences ADD COLUMN postgame_end_date DATE;
