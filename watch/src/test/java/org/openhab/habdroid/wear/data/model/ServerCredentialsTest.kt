package org.openhab.habdroid.wear.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openhab.habdroid.wear.shared.model.ServerCredentials

class ServerCredentialsTest {

    @Test
    fun `isConfigured true when serverUrl is set`() {
        assertTrue(ServerCredentials(serverUrl = "https://myopenhab.org").isConfigured)
    }

    @Test
    fun `isConfigured false when serverUrl is blank`() {
        assertFalse(ServerCredentials(serverUrl = "").isConfigured)
    }

    @Test
    fun `isConfigured false when serverUrl is whitespace`() {
        assertFalse(ServerCredentials(serverUrl = "   ").isConfigured)
    }

    @Test
    fun `hasAuth true when both username and password set`() {
        assertTrue(ServerCredentials(serverUrl = "x", username = "user", password = "pass").hasAuth)
    }

    @Test
    fun `hasAuth false when username is blank`() {
        assertFalse(ServerCredentials(serverUrl = "x", username = "", password = "pass").hasAuth)
    }

    @Test
    fun `hasAuth false when password is blank`() {
        assertFalse(ServerCredentials(serverUrl = "x", username = "user", password = "").hasAuth)
    }

    @Test
    fun `hasAuth false when both are blank`() {
        assertFalse(ServerCredentials(serverUrl = "x", username = "", password = "").hasAuth)
    }

    @Test
    fun `defaults have empty username and password`() {
        val cred = ServerCredentials(serverUrl = "https://myopenhab.org")
        assertFalse(cred.hasAuth)
    }
}
