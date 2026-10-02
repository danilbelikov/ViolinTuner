package com.violinjourney.app.core.recording

/**
 * Whose a recording made on a screen is (plan D13): a take of a piece (spec 3.15, 3.19) or a recording of an event (spec 3.35). The
 * session keeps it as its `pieceId` or its `eventId`; a recording of Live belongs to no one and has no owner.
 */
sealed interface TakeOwner {
    data class Piece(val id: Long) : TakeOwner

    data class Event(val id: Long) : TakeOwner
}

/** The piece a recording of [this] owner is a take of; null for an event's. */
val TakeOwner?.pieceId: Long? get() = (this as? TakeOwner.Piece)?.id

/** The event a recording of [this] owner belongs to; null for a piece's. */
val TakeOwner?.eventId: Long? get() = (this as? TakeOwner.Event)?.id
