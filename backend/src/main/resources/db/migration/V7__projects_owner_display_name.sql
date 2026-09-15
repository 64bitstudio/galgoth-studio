-- Ticket 086 (docs/definiciones/proyectos-por-usuario-y-explorar.md,
-- Diseño técnico §5, VoBo de Marco) -- galgoth-studio no tiene tabla de
-- usuarios propia (identidad delegada 100% a auth-core-mc) y el JWT solo
-- trae sub/role/tenant_id, nunca un nombre. Para mostrar "por Fulano
-- Pérez" en Explorar sin construir una integración nueva contra
-- auth-core-mc, se denormaliza el nombre a mostrar: el frontend ya
-- conoce sessionStore.user.nombre/apellidos al crear el proyecto, se
-- graba tal cual (puede quedar desactualizado si el usuario cambia su
-- nombre después -- tradeoff aceptado por simplicidad, documentado).
ALTER TABLE projects ADD COLUMN owner_display_name VARCHAR(255);
