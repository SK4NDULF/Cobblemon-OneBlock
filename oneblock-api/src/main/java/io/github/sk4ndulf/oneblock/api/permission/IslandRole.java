package io.github.sk4ndulf.oneblock.api.permission;

/**
 * A player's role in relation to a specific island. Simplified three-tier model
 * (see project plan): visitors may enter and look — nothing else.
 */
public enum IslandRole {
    OWNER,
    MEMBER,
    VISITOR
}
