package de.conduit.users;

import java.util.Set;
import java.util.UUID;

public interface Following {
    Set<UUID> findFollowedIds(UUID viewerID, Set<UUID> authorIDs);
}
