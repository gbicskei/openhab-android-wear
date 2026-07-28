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
import org.openhab.habdroid.wear.data.model.ServerCredentials
import org.openhab.habdroid.wear.data.repository.CredentialStore

class AuthInterceptorTest {

    private fun createInterceptor(credentials: ServerCredentials?): AuthInterceptor {
        val credentialStore = mockk<CredentialStore>()
        every { credentialStore.credentials } returns flowOf(credentials)
        return AuthInterceptor(credentialStore)
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
    fun `adds Basic Auth header when credentials set`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org", username = "user@test.com", password = "secret")
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        val authHeader = response.request.header("Authorization")
        assertTrue(authHeader != null && authHeader.startsWith("Basic "))
    }

    @Test
    fun `no auth header when username is blank`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org", username = "", password = "secret")
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertNull(response.request.header("Authorization"))
    }

    @Test
    fun `no auth header when password is blank`() {
        val interceptor = createInterceptor(
            ServerCredentials(serverUrl = "https://myopenhab.org", username = "user@test.com", password = "")
        )
        val chain = mockChain("https://placeholder.openhab.org/rest/items")

        val response = interceptor.intercept(chain)

        assertNull(response.request.header("Authorization"))
    }

    @Test
    fun `passes request unchanged when no credentials`() {
        val interceptor = createInterceptor(null)
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
