-- Ticket 073 (rediseño del detalle de proyecto) -- descripción libre y opcional
-- del proyecto, editable desde la misma pantalla (mismo lápiz que el nombre).
-- Nullable a propósito: ningún proyecto existente tiene descripción todavía,
-- y crear un proyecto nuevo tampoco la pide (se agrega después, si se quiere).
alter table projects add column description text;
