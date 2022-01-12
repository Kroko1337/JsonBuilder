package me.krokoyt.json.api

data class Dependency(val groupId: String, val artifactId: String, val version: String, var noRepository: Boolean = false)
