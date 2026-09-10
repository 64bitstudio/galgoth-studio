-- Ticket 054 (docs/definiciones/galgoth-studio-fase3-textura.md, Diseño
-- técnico §11/§12/§18, HU-39): pipeline de generación/regeneración de
-- textura por IA -- `ai_jobs` gana 2 `job_type` nuevos y una columna para
-- saber qué bone se regeneró.
--
-- job_type='generate_texture' (HU-36, "Generar con IA" sobre el modelo
--   completo): un solo job cubre TODOS los bones con geometría del mob --
--   target_bone_id queda NULL (no hay "un" bone objetivo, son todos).
-- job_type='edit_texture' (HU-37, "Regenerar textura" de UN bone
--   puntual): target_bone_id NOT NULL, identifica exactamente qué bone.
--
-- Nombre real de la constraint verificado contra el esquema ya migrado
-- (V1, sin nombre explícito en el `check` inline de la columna
-- `job_type`) -- Postgres la nombró automáticamente `ai_jobs_job_type_check`
-- (`<tabla>_<columna>_check`, convención estándar para un `check` de
-- columna sin `constraint <nombre>` explícito).
alter table ai_jobs drop constraint ai_jobs_job_type_check;
alter table ai_jobs add constraint ai_jobs_job_type_check
    check (job_type in ('generate', 'edit', 'generate_texture', 'edit_texture'));

-- Hallazgo real, señalado explícitamente (sin parche silencioso): el
-- ticket 054 y el Diseño técnico §18 solo piden la migración de arriba +
-- la columna de abajo -- pero `chk_ai_jobs_base_values_by_type` (V1,
-- ticket 003) todavía exige `job_type='generate' -> base_* NULL` /
-- `job_type='edit' -> base_* NOT NULL`, una whitelist cerrada de 2
-- valores. A diferencia de 'generate' (arranca de un mob SIN geometría
-- todavía), 'generate_texture'/'edit_texture' SIEMPRE corren sobre un mob
-- con geometría ya usable (revision_number >= 1, AC del ticket 054) --
-- necesitan `base_revision_number`/`base_draft_version` NOT NULL, igual
-- que 'edit', para que el chequeo de conflicto 409 (Diseño técnico §16)
-- tenga algo contra qué comparar. Sin esta corrección, CUALQUIER insert
-- de 'generate_texture'/'edit_texture' con base_* poblados (el único
-- caso válido) violaría esta constraint -- se actualiza en el mismo
-- movimiento, documentado acá para que no se lea como un cambio "de
-- paso" no relacionado.
alter table ai_jobs drop constraint chk_ai_jobs_base_values_by_type;
alter table ai_jobs add constraint chk_ai_jobs_base_values_by_type check (
    (job_type = 'generate' and base_revision_number is null and base_draft_version is null)
    or
    (job_type in ('edit', 'generate_texture', 'edit_texture')
        and base_revision_number is not null and base_draft_version is not null)
);

-- Nullable: NULL para 'generate_texture' (todos los bones), poblado para
-- 'edit_texture' (regeneración de un bone puntual, HU-37).
alter table ai_jobs add column target_bone_id text;
