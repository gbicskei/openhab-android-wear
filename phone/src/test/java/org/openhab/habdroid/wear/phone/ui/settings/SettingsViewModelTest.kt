package org.openhab.habdroid.wear.phone.ui.settings

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var credentialStore: PhoneCredentialStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        credentialStore = mockk(relaxed = true)
        every { credentialStore.isDebugMode } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(credentialStore)

    @Test
    fun `initial state loads debug mode from store`() {
        every { credentialStore.isDebugMode } returns true
        val vm = createViewModel()

        assertTrue(vm.uiState.value.debugMode)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertFalse(vm.uiState.value.needsSync)
    }

    @Test
    fun `setDebugMode updates UI state but does not persist`() {
        val vm = createViewModel()

        vm.setDebugMode(true)

        assertTrue(vm.uiState.value.debugMode)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        // Should NOT have persisted yet
        coVerify(exactly = 0) { credentialStore.setDebugMode(any()) }
    }

    @Test
    fun `save persists debug mode and marks needsSync`() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.setDebugMode(true)
        vm.save()
        advanceUntilIdle()

        coVerify { credentialStore.setDebugMode(true) }
        coVerify { credentialStore.markSettingsNeedSync() }
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertTrue(vm.uiState.value.needsSync)
    }

    @Test
    fun `save updates legacy debugMode flow`() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.setDebugMode(true)
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.debugMode.value)
    }

    @Test
    fun `toggling debug mode off and saving persists false`() = runTest(testDispatcher) {
        every { credentialStore.isDebugMode } returns true
        val vm = createViewModel()

        vm.setDebugMode(false)
        vm.save()
        advanceUntilIdle()

        coVerify { credentialStore.setDebugMode(false) }
    }
}
