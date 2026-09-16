-- Hallazgo real de seguridad (2026-09-15, auth-core-mc#071): la URL del
-- avatar público (`/api/account/avatar/{userId}`) exponía el UUID real
-- de identidad del dueño (el mismo `sub` que auth-core-mc usa en sus
-- JWTs) a cualquier visitante de Explorar (sin sesión) -- ese UUID era
-- exactamente el dato que hacía explotable un secuestro de cuenta contra
-- endpoints de auth-core-mc que confiaban un `userId` sin autenticar
-- (ya cerrado del lado de auth-core-mc, ver ese repo). Corregido acá:
-- un identificador público separado, sin relación reconstruible con el
-- id de identidad real, generado una vez por perfil.
--
-- Puramente aditiva: DEFAULT gen_random_uuid() rellena tanto las filas
-- existentes como las nuevas -- ningún avatar ya subido se pierde ni
-- cambia de contenido, solo la URL pública con la que se sirve.
ALTER TABLE user_profile ADD COLUMN public_avatar_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX user_profile_public_avatar_id_idx ON user_profile (public_avatar_id);
