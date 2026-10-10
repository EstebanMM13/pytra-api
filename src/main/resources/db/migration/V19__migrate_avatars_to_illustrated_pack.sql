-- Preset avatars moved from the icon-on-gradient set to the illustrated SVG pack (see AvatarPolicy).
-- Old keys with an obvious equivalent are remapped; any other value outside the new 48 keys is cleared,
-- so users fall back to their username initial.
UPDATE users SET avatar = CASE avatar
    WHEN 'gamepad' THEN 'mando'
    WHEN 'swords' THEN 'espada'
    WHEN 'shield' THEN 'escudo'
    WHEN 'ghost' THEN 'fantasmapx'
    WHEN 'skull' THEN 'calavera'
    ELSE avatar
END
WHERE avatar IN ('gamepad', 'swords', 'shield', 'ghost', 'skull');

UPDATE users SET avatar = NULL
WHERE avatar IS NOT NULL
  AND avatar NOT IN (
    'mando', 'portatil', 'joystick', 'cartucho', 'auriculares', 'recreativa', 'raton', 'disco',
    'espada', 'pocion', 'corazon', 'moneda', 'llave', 'cofre', 'escudo', 'bomba',
    'barras', 'play', 'pixeles', 'anillo', 'triangulos', 'ondas', 'rombo', 'orbita',
    'zorro', 'buho', 'gato', 'rana', 'oso', 'pulpo', 'pinguino', 'conejo',
    'heroe', 'invasor', 'fantasmapx', 'setapx', 'corazonpx', 'espadapx', 'slimepx', 'calavera',
    'rpg', 'shooter', 'carreras', 'puzzle', 'terror', 'plataformas', 'estrategia', 'deportes');
