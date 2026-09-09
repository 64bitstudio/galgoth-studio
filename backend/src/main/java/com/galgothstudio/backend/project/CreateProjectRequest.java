package com.galgothstudio.backend.project;

/** Body de `POST /api/projects`. HU-01 AC #3: solo se pide nombre -- sin campos técnicos de IA/geometría. */
public record CreateProjectRequest(String name) {
}
