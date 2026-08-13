package io.github.sk4ndulf.cobblemon.oneblock.api.party;

import java.util.UUID;

/**
 * Party/co-op queries. Membership itself is exposed on {@code Island} ({@code owner()},
 * {@code members()}); this interface covers invite state and limits. Mutations flow
 * through player commands — addons react via {@code PartyJoinEvent}/{@code PartyLeaveEvent}.
 *
 * <p>All methods must be called on the server thread.</p>
 */
public interface PartyManager {

    /** Whether the player currently has a pending (unexpired) island invite. */
    boolean hasPendingInvite(UUID player);

    /** Maximum party size including the owner, as configured by the wizard. */
    int maxPartySize();
}
