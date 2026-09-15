-- Ticket 091 (docs/definiciones/perfil-de-usuario.md, VoBo de Marco) --
-- "perfil de producto" de cada usuario de auth-core-mc: avatar (MinIO,
-- ya wireado en este backend, ticket 023) y preferencias de producto.
-- user_id = `sub` del JWT -- sin FK, mismo criterio que `projects.owner_ref`
-- (ticket 084): auth-core-mc es la fuente de verdad de identidad, esto es
-- un dato de PRODUCTO ligado por id.
CREATE TABLE user_profile (
    user_id UUID PRIMARY KEY,
    avatar_key VARCHAR(255),
    avatar_content_type VARCHAR(50),
    notify_email BOOLEAN NOT NULL DEFAULT true,
    notify_product_news BOOLEAN NOT NULL DEFAULT true,
    notify_save_reminders BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
