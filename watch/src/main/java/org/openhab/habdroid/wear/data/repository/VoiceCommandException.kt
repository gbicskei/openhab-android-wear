package org.openhab.habdroid.wear.data.repository

/**
 * Exception thrown when the openHAB voice interpreter returns an error response.
 * The [message] contains a user-friendly error description parsed from the server response.
 */
class VoiceCommandException(override val message: String) : Exception(message)
