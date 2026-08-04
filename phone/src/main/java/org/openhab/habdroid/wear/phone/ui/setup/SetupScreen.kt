package org.openhab.habdroid.wear.phone.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openhab.habdroid.wear.phone.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Intercept back when there are unsaved changes
    val handleBack = {
        if (uiState.hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    if (showDiscardDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved connection settings.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDiscardDialog = false
                }) { Text("Keep editing") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection") },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── User Key (Multi-user) ───
            OutlinedTextField(
                value = uiState.userKey,
                onValueChange = viewModel::onUserKeyChanged,
                label = { Text("User Key") },
                placeholder = { Text("e.g. gabor, anna") },
                supportingText = { Text("Optional. Separates tile config per user on the same server.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── Main Server (Remote) ───
            SectionHeader(
                title = "Main Server",
                helpText = "The server your watch connects to at runtime for item states, commands, and real-time updates. Can be myopenhab.org (cloud) or your local server if it's accessible from the internet."
            )

            MainServerFields(
                serverUrl = uiState.serverUrl,
                username = uiState.username,
                password = uiState.password,
                passwordPlaceholder = uiState.passwordPlaceholder,
                passwordModified = uiState.passwordModifiedThisSession,
                connectionStatus = uiState.connectionStatus,
                errorMessage = uiState.errorMessage,
                canTest = uiState.canTest,
                onServerUrlChanged = viewModel::onServerUrlChanged,
                onUsernameChanged = viewModel::onUsernameChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onTest = viewModel::testConnection
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── Config Server ───
            SectionHeader(
                title = "Config Server",
                helpText = "Your local openHAB server, used by this app to save tile and complication configurations. Required because myopenhab.org doesn't support REST write operations. Only needed during setup — the watch doesn't use it."
            )

            ConfigServerFields(
                serverUrl = uiState.configServerUrl,
                username = uiState.configUsername,
                password = uiState.configPassword,
                passwordPlaceholder = uiState.configPasswordPlaceholder,
                passwordModified = uiState.configPasswordModifiedThisSession,
                apiToken = uiState.configApiToken,
                useApiToken = uiState.configUseApiToken,
                connectionStatus = uiState.configConnectionStatus,
                errorMessage = uiState.configErrorMessage,
                canTest = uiState.canTestConfig,
                onServerUrlChanged = viewModel::onConfigServerUrlChanged,
                onUsernameChanged = viewModel::onConfigUsernameChanged,
                onPasswordChanged = viewModel::onConfigPasswordChanged,
                onApiTokenChanged = viewModel::onConfigApiTokenChanged,
                onAuthModeChanged = viewModel::onConfigAuthModeChanged,
                onTest = viewModel::testConfigConnection
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Save Button ───
            androidx.compose.material3.Button(
                onClick = viewModel::saveAll,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MainServerFields(
    serverUrl: String,
    username: String,
    password: String,
    passwordPlaceholder: String,
    passwordModified: Boolean,
    connectionStatus: ConnectionStatus,
    errorMessage: String?,
    canTest: Boolean,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTest: () -> Unit
) {
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChanged,
        label = { Text(stringResource(R.string.server_url_label)) },
        placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChanged,
        label = { Text(stringResource(R.string.username_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Username + ContentType.EmailAddress }
    )

    PasswordField(
        password = password,
        passwordPlaceholder = passwordPlaceholder,
        passwordModified = passwordModified,
        onPasswordChanged = onPasswordChanged
    )

    TestButton(connectionStatus = connectionStatus, errorMessage = errorMessage, canTest = canTest, onTest = onTest)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigServerFields(
    serverUrl: String,
    username: String,
    password: String,
    passwordPlaceholder: String,
    passwordModified: Boolean,
    apiToken: String,
    useApiToken: Boolean,
    connectionStatus: ConnectionStatus,
    errorMessage: String?,
    canTest: Boolean,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onApiTokenChanged: (String) -> Unit,
    onAuthModeChanged: (Boolean) -> Unit,
    onTest: () -> Unit
) {
    // Server URL (always shown)
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChanged,
        label = { Text(stringResource(R.string.server_url_label)) },
        placeholder = { Text("https://openhab.example.com") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )

    // Auth mode selector
    Text("Authentication", style = MaterialTheme.typography.labelMedium)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !useApiToken,
            onClick = { onAuthModeChanged(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("Basic Auth") }
        SegmentedButton(
            selected = useApiToken,
            onClick = { onAuthModeChanged(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("API Token") }
    }

    if (useApiToken) {
        // API Token input (masked like a password)
        var tokenVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = apiToken,
            onValueChange = onApiTokenChanged,
            label = { Text("API Token") },
            placeholder = { Text("oh.tokenname.xxxxx") },
            supportingText = { Text("Generate in openHAB Settings > API Security") },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                if (apiToken.isNotEmpty()) {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (tokenVisible) "Hide" else "Show"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // Basic Auth fields
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChanged,
            label = { Text(stringResource(R.string.username_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Username + ContentType.EmailAddress }
        )

        PasswordField(
            password = password,
            passwordPlaceholder = passwordPlaceholder,
            passwordModified = passwordModified,
            onPasswordChanged = onPasswordChanged
        )
    }

    TestButton(connectionStatus = connectionStatus, errorMessage = errorMessage, canTest = canTest, onTest = onTest)
}

@Composable
private fun PasswordField(
    password: String,
    passwordPlaceholder: String,
    passwordModified: Boolean,
    onPasswordChanged: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    // Show masked dots as the value when a password is stored but not yet modified this session
    val displayValue = if (!passwordModified && passwordPlaceholder.isNotEmpty()) passwordPlaceholder else password
    OutlinedTextField(
        value = displayValue,
        onValueChange = onPasswordChanged,
        label = { Text(stringResource(R.string.password_label)) },
        singleLine = true,
        visualTransformation = if (passwordVisible && passwordModified)
            VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            if (passwordModified) {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide" else "Show"
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Password }
    )
}

@Composable
private fun TestButton(
    connectionStatus: ConnectionStatus,
    errorMessage: String?,
    canTest: Boolean,
    onTest: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(onClick = onTest, enabled = canTest) {
            Text(stringResource(R.string.test_connection))
        }
        when (connectionStatus) {
            ConnectionStatus.Idle -> {}
            ConnectionStatus.Testing -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            ConnectionStatus.Success -> {
                Icon(Icons.Default.CheckCircle, "Connected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            ConnectionStatus.Failed -> {
                Icon(Icons.Default.Error, "Failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
    }

    AnimatedVisibility(visible = errorMessage != null) {
        Text(
            text = errorMessage ?: "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    helpText: String
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Outlined.HelpOutline,
                contentDescription = "Help",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    AnimatedVisibility(visible = expanded) {
        Text(
            text = helpText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
