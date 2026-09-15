package com.galgothstudio.backend.account;

/** Ticket 091 -- respuesta de {@code POST /api/account/avatar}: la ruta servible del avatar recién subido. */
public record AvatarResponse(String avatarUrl) {
}
