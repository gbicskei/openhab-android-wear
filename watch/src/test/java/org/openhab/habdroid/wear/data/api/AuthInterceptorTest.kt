package org.openhab.habdroid.wear.data.api

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.LocalCredentials

class AuthInterceptorTest {

    private fun createInterceptor(
        credentials: ServerCredentials?,
        localCredentials: LocalCredentials = LocalCredentials(),
        isLocalActive: Boolean = false,
        activeUrl: String? = credentials?.serverUrl
    ): AuthInterceptor {
        val credentialStore = mockk<CredentialStore>()
        every { credentialStore.credentials } returns flowOf(credentials)
        every { credentialStore.localCredentials } returns flowOf(localCredentials)

        val serverSelector = mockk<ServerSelector>()
        every { serverSelector.getActiveUrlOrDefault() } returns activeUrl
        every { serverSelector.isLocalActive() } returns isLocalActive

        return AuthInterceptor(credentialStore, serverSelector)
    }

    private fun mockChain(url: String): Interceptor.Chain {
        val request = Request.Builder().url(url).build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            val req = firstArg<Request>()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }
        return chain
    }

    @Test
    fun `replaces placeholder URL with server URL`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org", username = "u", password = "p")
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertEquals("https://myopenhab.org/rest/items", response.request.url.toString())
    }

    @Test
    fun `replaces placeholder URL and preserves path`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org/", username = "u", password = "p")
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items?metadata=wearTile")

        val response = interceptor.intercept(chain)

        assertEquals("https://myopenhab.org/rest/items?metadata=wearTile", response.request.url.toString())
    }

    @Test
    fun `adds Basic Auth header when cloud is active`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org", username = "user@test.com", password = "secret"),
            isLocalActive = false
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        val authHeader = response.request.header("Authorization")
        assertTrue(authHeader != null && authHeader.startsWith("Basic "))
    }

    @Test
    fun `uses Bearer token when local is active and apiToken is set`() {
        val interceptor = createInterceptor(
            credentials = ServerCredentials(serverUrl = "https://myopenhab.org", username = "u", password = "p"),
            localCredentials = LocalCredentials(apiToken = "oh.mytoken123"),
            isLocalActive = true,
            activeUrl = "http://192.168.1.100:8080"
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertEquals("Bearer oh.mytoken123", response.request.header("Authorization"))
    }

    @Test
    fun `uses Basic Auth when local is active with username and password`() {
        val interceptor = createInterceptor(
            credentials = ServerCredentials(serverUrl = "https://myopenhab.org", username = "cloud", password = "cloudpass"),
            localCredentials = LocalCredentials(username = "localuser", password = "localpass"),
            isLocalActive = true,
            activeUrl = "http://192.168.1.100:8080"
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        val authHeader = response.request.header("Authorization")
        assertTrue(authHeader != null && authHeader.startsWith("Basic "))
        // Verify it's NOT the cloud credentials by checking the decoded value contains localuser
        val decoded = String(java.util.Base64.getDecoder().decode(authHeader!!.removePrefix("Basic ")))
        assertEquals("localuser:localpass", decoded)
    }

    @Test
    fun `no auth header when local is active with no credentials`() {
        val interceptor = createInterceptor(
            credentials = ServerCredentials(serverUrl = "https://myopenhab.org", username = "cloud", password = "cloudpass"),
            localCredentials = LocalCredentials(), // all empty
            isLocalActive = true,
            activeUrl = "http://192.168.1.100:8080"
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertNull(response.request.header("Authorization"))
    }

    @Test
    fun `Bearer takes precedence over Basic Auth for local`() {
        val interceptor = createInterceptor(
            credentials = ServerCredentials(serverUrl = "https://myopenhab.org", username = "u", password = "p"),
            localCredentials = LocalCredentials(username = "localuser", password = "localpass", apiToken = "oh.token"),
            isLocalActive = true,
            activeUrl = "http://192.168.1.100:8080"
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertEquals("Bearer oh.token", response.request.header("Authorization"))
    }

    @Test
    fun `no auth header when cloud username is blank`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org", username = "", password = "secret"),
            isLocalActive = false
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertNull(response.request.header("Authorization"))
    }

    @Test
    fun `no auth header when cloud password is blank`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org", username = "user@test.com", password = ""),
            isLocalActive = false
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertNull(response.request.header("Authorization"))
    }

    @Test
    fun `passes request unchanged when no credentials`() {
        val credentialStore = mockk<CredentialStore>()
        every { credentialStore.credentials } returns flowOf(null)
        every { credentialStore.localCredentials } returns flowOf(LocalCredentials())

        val serverSelector = mockk<ServerSelector>()
        every { serverSelector.getActiveUrlOrDefault() } returns null
        every { serverSelector.isLocalActive() } returns false

        val interceptor = AuthInterceptor(credentialStore, serverSelector)
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        // URL unchanged — placeholder not replaced
        assertEquals("https://placeholder.openhab.org/rest/items", response.request.url.toString())
        assertNull(response.request.header("Authorization"))
    }

    @Test
    fun `handles server URL without trailing slash`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org", username = "u", password = "p")
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertEquals("https://myopenhab.org/rest/items", response.request.url.toString())
    }

    @Test
    fun `handles server URL with trailing slash`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org/", username = "u", password = "p")
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertEquals("https://myopenhab.org/rest/items", response.request.url.toString())
    }
}
