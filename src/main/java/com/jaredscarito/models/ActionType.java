package com.jaredscarito.models;

public enum ActionType {
    /**
     * Tickets
     */
    UNLOCK_TICKET,
    LOCK_TICKET,
    CLOSE_TICKET,
    CREATE_TICKET,
    TICKET_ADD_MEMBER,
    TICKET_REMOVE_MEMBER,
    /**
     * Sticky
     */
    STICKY_CREATE,
    STICKY_EDIT,
    STICKY_REMOVE,
    /**
     * Mutes
     */
    MUTE_CREATE,
    MUTE_EDIT,
    MUTE_DELETE,
    /**
     * Kicks
     */
    KICK_CREATE,
    /**
     * Blacklists
     */
    BLACKLIST_CREATE,
    BLACKLIST_REMOVE,
    /**
     * Bans
     */
    BAN_CREATE,
    BAN_REMOVE,
    /**
     * Lockdown
     */
    LOCKDOWN_START,
    LOCKDOWN_END
}
