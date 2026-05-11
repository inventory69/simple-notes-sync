package dev.dettmer.simplenotes.noteimport.keep.conflict

/**
 * v2.5.0 — User-wählbare Konflikt-Strategie aus dem Configuring-Dialog (§4.2 [3]).
 *
 *  - [ALWAYS_CREATE]: jede importierte Notiz wird neu erstellt; Hash-Match egal.
 *    Sicherste Option (Default), kann zu Duplikaten führen.
 *  - [SKIP]: bei Hash-Match wird übersprungen, sonst neu erstellt.
 *  - [REPLACE]: bei Hash-Match wird die existierende Notiz **inhaltlich**
 *    überschrieben (id bleibt erhalten); sonst neu erstellt.
 */
enum class ConflictStrategy { ALWAYS_CREATE, SKIP, REPLACE }
