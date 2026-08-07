package org.openhab.habdroid.wear.ui.voice

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantRegistrarTest {

    private lateinit var registrar: AssistantRegistrar
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setup() {
        registrar = AssistantRegistrar()
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
    }

    @Test
    fun `hasWriteSecureSettings returns true when permission granted`() {
        every {
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        } returns PackageManager.PERMISSION_GRANTED

        assertTrue(registrar.hasWriteSecureSettings(context))
    }

    @Test
    fun `hasWriteSecureSettings returns false when permission denied`() {
        every {
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        } returns PackageManager.PERMISSION_DENIED

        assertFalse(registrar.hasWriteSecureSettings(context))
    }

    @Test
    fun `isRegistered returns true when voice_interaction_service contains package name`() {
        mockkStatic(android.provider.Settings.Secure::class)
        every {
            android.provider.Settings.Secure.getString(contentResolver, "voice_interaction_service")
        } returns "org.openhab.habdroid.wear/org.openhab.habdroid.wear.ui.voice.OpenHabVoiceInteractionService"

        assertTrue(registrar.isRegistered(context))
    }

    @Test
    fun `isRegistered returns false when voice_interaction_service is null`() {
        mockkStatic(android.provider.Settings.Secure::class)
        every {
            android.provider.Settings.Secure.getString(contentResolver, "voice_interaction_service")
        } returns null

        assertFalse(registrar.isRegistered(context))
    }

    @Test
    fun `isRegistered returns false when voice_interaction_service is different app`() {
        mockkStatic(android.provider.Settings.Secure::class)
        every {
            android.provider.Settings.Secure.getString(contentResolver, "voice_interaction_service")
        } returns "com.google.android.wearable.assistant/some.Service"

        assertFalse(registrar.isRegistered(context))
    }

    @Test
    fun `register returns false when permission not granted`() {
        every {
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        } returns PackageManager.PERMISSION_DENIED

        assertFalse(registrar.register(context))
    }

    @Test
    fun `register writes settings when permission granted`() {
        every {
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(android.provider.Settings.Secure::class)
        every {
            android.provider.Settings.Secure.putString(contentResolver, any(), any())
        } returns true

        assertTrue(registrar.register(context))

        verify {
            android.provider.Settings.Secure.putString(
                contentResolver,
                "voice_interaction_service",
                "org.openhab.habdroid.wear/org.openhab.habdroid.wear.ui.voice.OpenHabVoiceInteractionService"
            )
        }
        verify {
            android.provider.Settings.Secure.putString(
                contentResolver,
                "assistant",
                "org.openhab.habdroid.wear/org.openhab.habdroid.wear.ui.voice.VoiceCommandActivity"
            )
        }
    }

    @Test
    fun `ensureRegistered does nothing when permission not granted`() {
        every {
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        } returns PackageManager.PERMISSION_DENIED

        registrar.ensureRegistered(context)

        // No settings writes should happen
        verify(exactly = 0) {
            android.provider.Settings.Secure.putString(contentResolver, any(), any())
        }
    }

    @Test
    fun `ensureRegistered does nothing when already registered`() {
        every {
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(android.provider.Settings.Secure::class)
        every {
            android.provider.Settings.Secure.getString(contentResolver, "voice_interaction_service")
        } returns "org.openhab.habdroid.wear/org.openhab.habdroid.wear.ui.voice.OpenHabVoiceInteractionService"

        registrar.ensureRegistered(context)

        // No writes — already registered
        verify(exactly = 0) {
            android.provider.Settings.Secure.putString(contentResolver, any(), any())
        }
    }

    @Test
    fun `getStatus returns correct status`() {
        every {
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(android.provider.Settings.Secure::class)
        every {
            android.provider.Settings.Secure.getString(contentResolver, "voice_interaction_service")
        } returns "org.openhab.habdroid.wear/org.openhab.habdroid.wear.ui.voice.OpenHabVoiceInteractionService"

        val status = registrar.getStatus(context)
        assertTrue(status.hasPermission)
        assertTrue(status.isRegistered)
    }
}
