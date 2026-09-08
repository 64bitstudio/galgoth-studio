-- Esquema inicial — Galgoth Studio Technical Alpha.
-- Fuente: docs/definiciones/galgoth-studio-mvp.md (Diseño técnico §2 y
-- "Estado inicial de un mob"). Ticket 003.
--
-- Decisiones de implementación tomadas en este ticket (resuelven la
-- "Nota abierta de implementación" del documento sobre FK compuesta vs.
-- referencia lógica):
--   - ai_jobs.base_revision_number / exports.revision_number SÍ son FK
--     compuesta real hacia mob_revisions(mob_id, revision_number):
--     ambos referencian un snapshot INMUTABLE que existe para siempre,
--     así que la integridad referencial tiene sentido y vale la pena.
--     Postgres no valida una fila de FK compuesta si CUALQUIERA de sus
--     columnas es NULL (comportamiento MATCH SIMPLE, el default) — coincide
--     exactamente con la regla de negocio: base_revision_number es NULL
--     en jobs job_type='generate' y esa fila no debe validarse contra
--     mob_revisions todavía.
--   - ai_jobs.base_draft_version NO es FK: mob_drafts no tiene historial
--     (una sola fila mutable por mob), así que "base_draft_version" es un
--     valor snapshot para chequeo de concurrencia optimista en capa de
--     aplicación (HU-18), no una referencia a una fila que siga
--     existiendo con ese valor — una FK real rompería exactamente el caso
--     de uso que existe para detectar (que el draft avanzó desde que se
--     generó la propuesta).
--   - Enums como CHECK constraints sobre texto, no tipos ENUM nativos de
--     Postgres — más simple de evolucionar (agregar un valor nuevo es un
--     ALTER TABLE ... DROP/ADD CONSTRAINT, no un ALTER TYPE).

create table projects (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    owner_ref text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table mobs (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references projects (id),
    name text not null,
    base_type text not null
        check (base_type in ('humanoid', 'arachnid', 'quadruped', 'flying', 'custom')),
    status text not null
        check (status in ('draft', 'in_progress', 'ready')),
    -- current_revision_number=0 significa "sin ninguna revisión todavía"
    -- (mob recién creado, ver "Estado inicial de un mob" del documento).
    current_revision_number integer not null default 0,
    thumbnail_key text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
    -- SIN columna created_by a propósito: la autoría vive exclusivamente
    -- en mob_revisions.created_by (ver documento de definición, corrección
    -- de inconsistencia).
);

create index idx_mobs_project_id on mobs (project_id);

create table mob_revisions (
    id uuid primary key default gen_random_uuid(),
    mob_id uuid not null references mobs (id),
    revision_number integer not null,
    -- Snapshot completo e inmutable de MobProjectModel — nunca un delta.
    model_jsonb jsonb not null,
    created_by text not null check (created_by in ('user', 'ai', 'system')),
    change_summary text,
    created_at timestamptz not null default now(),
    -- revision_number es único por mob (no globalmente) — arranca en 1.
    -- También es el target de las FK compuestas de ai_jobs/exports de abajo.
    constraint uq_mob_revisions_mob_revnum unique (mob_id, revision_number)
);

create index idx_mob_revisions_mob_id on mob_revisions (mob_id);

create table mob_drafts (
    -- PK = mob_id: una sola fila mutable por mob. No existe fila hasta el
    -- primer commit persistido (ver "Estado inicial de un mob").
    mob_id uuid primary key references mobs (id),
    draft_model_jsonb jsonb not null,
    -- Se incrementa SOLO cuando el contenido cambia materialmente (ver
    -- Diseño técnico §4 del documento) — esa regla vive en capa de
    -- aplicación, no en esta migración (un CHECK no puede comparar contra
    -- el valor anterior de la misma fila).
    draft_version integer not null,
    updated_at timestamptz not null default now()
);

create table reference_images (
    id uuid primary key default gen_random_uuid(),
    mob_id uuid not null references mobs (id),
    storage_key text not null,
    width integer not null,
    height integer not null,
    content_type text not null,
    created_at timestamptz not null default now()
);

create index idx_reference_images_mob_id on reference_images (mob_id);

create table ai_jobs (
    id uuid primary key default gen_random_uuid(),
    mob_id uuid not null references mobs (id),
    job_type text not null check (job_type in ('generate', 'edit')),
    status text not null
        check (status in ('running', 'completed', 'failed', 'cancelled')),
    provider text not null,
    model text not null,
    prompt_version text not null,
    schema_version text not null,
    reference_ids jsonb not null default '[]'::jsonb,
    -- NULL cuando job_type='generate' (sin revisión/draft previos);
    -- siempre presentes cuando job_type='edit' -- exigido por el CHECK
    -- de abajo, no dejado a la buena voluntad de la capa de aplicación.
    base_revision_number integer,
    base_draft_version integer,
    proposal_jsonb jsonb,
    error text,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz,
    constraint chk_ai_jobs_base_values_by_type check (
        (job_type = 'generate' and base_revision_number is null and base_draft_version is null)
        or
        (job_type = 'edit' and base_revision_number is not null and base_draft_version is not null)
    ),
    -- FK compuesta real hacia mob_revisions -- ver nota de cabecera. Postgres
    -- no valida esta fila si base_revision_number es NULL (MATCH SIMPLE).
    constraint fk_ai_jobs_base_revision
        foreign key (mob_id, base_revision_number)
        references mob_revisions (mob_id, revision_number)
);

create index idx_ai_jobs_mob_id on ai_jobs (mob_id);

create table ai_job_events (
    id uuid primary key default gen_random_uuid(),
    job_id uuid not null references ai_jobs (id),
    seq integer not null,
    stage text not null,
    message text,
    progress_pct integer check (progress_pct between 0 and 100),
    -- Preview no persistente (preview_snapshot | preview_operations) --
    -- nunca toca mob_drafts/mob_revisions, ver Diseño técnico §5.
    payload_jsonb jsonb,
    created_at timestamptz not null default now(),
    constraint uq_ai_job_events_job_seq unique (job_id, seq)
);

create index idx_ai_job_events_job_id on ai_job_events (job_id);

create table exports (
    id uuid primary key default gen_random_uuid(),
    mob_id uuid not null references mobs (id),
    revision_number integer not null,
    format_version text not null check (format_version in ('v4', 'v5')),
    storage_key text not null,
    validation_report_jsonb jsonb not null,
    created_at timestamptz not null default now(),
    -- FK compuesta real hacia mob_revisions -- un export siempre apunta a
    -- una revisión que existió y sigue existiendo (inmutable).
    constraint fk_exports_revision
        foreign key (mob_id, revision_number)
        references mob_revisions (mob_id, revision_number)
);

create index idx_exports_mob_id on exports (mob_id);
