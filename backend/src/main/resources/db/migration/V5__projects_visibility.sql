-- Ticket 084: cada proyecto nace PRIVATE por defecto (decisión de Marco,
-- docs/definiciones/proyectos-por-usuario-y-explorar.md) -- publicarlo es
-- una acción explícita del dueño (ticket 086).
ALTER TABLE projects ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE projects ADD CONSTRAINT chk_projects_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC'));
