package com.andrelair.ktayl.claims.application;

/** Who is acting on a claim + their authority. In v1 the identity comes from a header; real auth =
 *  Authentik OIDC (ADR-005) later. */
public record Actor(String id, Authority authority) {}
