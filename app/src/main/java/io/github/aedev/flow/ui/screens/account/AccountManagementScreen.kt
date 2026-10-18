package io.github.aedev.flow.ui.screens.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.account.AccountProfile
import io.github.aedev.flow.data.account.auth.YouTubeTvAuthManager
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

@Composable
fun AccountManagementScreen(
    onBackClick: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val tvAuthState by viewModel.tvAuthState.collectAsStateWithLifecycle()
    val cookieError by viewModel.cookieError.collectAsStateWithLifecycle()

    var showTvAuthDialog by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var profileToRemove by remember { mutableStateOf<AccountProfile?>(null) }

    Scaffold(
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.youtube_accounts),
                onBack = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ActiveAccountCard(activeAccount = activeAccount)
            }

            item {
                Text(
                    text = stringResource(R.string.switch_account),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            item {
                // Anonymous account option
                ProfileCardItem(
                    name = stringResource(R.string.guest_incognito),
                    handle = stringResource(R.string.no_youtube_account),
                    avatarUrl = null,
                    isSelected = activeAccount == null,
                    onSelect = { viewModel.switchAccount(null) },
                    onDelete = null,
                )
            }

            items(profiles, key = { it.id }) { profile ->
                ProfileCardItem(
                    name = profile.name,
                    handle = profile.channelHandle ?: profile.email ?: "",
                    avatarUrl = profile.avatarUrl,
                    isSelected = profile.id == activeAccount?.id,
                    onSelect = { viewModel.switchAccount(profile.id) },
                    onDelete = { profileToRemove = profile },
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.add_youtube_account),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.startTvActivation()
                            showTvAuthDialog = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.activate_tv_code))
                    }

                    OutlinedButton(
                        onClick = { showCookieDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Cookie, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_cookies))
                    }
                }
            }
        }
    }

    if (showTvAuthDialog) {
        TvActivationDialog(
            tvAuthState = tvAuthState,
            onDismiss = {
                showTvAuthDialog = false
                viewModel.resetTvActivationState()
            },
        )
    }

    if (showCookieDialog) {
        CookieImportDialog(
            errorMessage = cookieError,
            onConfirm = { cookie ->
                viewModel.authenticateWithCookie(cookie)
            },
            onDismiss = {
                showCookieDialog = false
                viewModel.clearCookieError()
            },
        )
    }

    profileToRemove?.let { profile ->
        FlowAlertDialog(
            onDismissRequest = { profileToRemove = null },
            title = { Text(stringResource(R.string.remove_account_title)) },
            text = { Text(stringResource(R.string.remove_account_body, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeAccount(profile.id)
                        profileToRemove = null
                    },
                ) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ActiveAccountCard(activeAccount: AccountProfile?) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            if (!activeAccount?.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = activeAccount?.avatarUrl,
                    contentDescription = activeAccount?.name,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeAccount?.name ?: stringResource(R.string.guest_incognito),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = activeAccount?.channelHandle ?: activeAccount?.email ?: stringResource(R.string.no_youtube_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ProfileCardItem(
    name: String,
    handle: String,
    avatarUrl: String?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (handle.isNotBlank()) {
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect,
            )
        }
    }
}

@Composable
private fun TvActivationDialog(
    tvAuthState: YouTubeTvAuthManager.TvAuthState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tv_activation_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (tvAuthState) {
                    is YouTubeTvAuthManager.TvAuthState.Initial -> {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }

                    is YouTubeTvAuthManager.TvAuthState.CodeReceived -> {
                        Text(
                            text = stringResource(R.string.tv_activation_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = tvAuthState.userCode,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier =
                                    Modifier
                                        .padding(16.dp)
                                        .align(Alignment.CenterHorizontally),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(tvAuthState.userCode))
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.btn_copy))
                            }

                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tvAuthState.verificationUrl))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.open))
                            }
                        }
                    }

                    is YouTubeTvAuthManager.TvAuthState.Polling -> {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }

                    is YouTubeTvAuthManager.TvAuthState.Success -> {
                        Text(text = stringResource(R.string.done))
                    }

                    is YouTubeTvAuthManager.TvAuthState.Error -> {
                        Text(
                            text = tvAuthState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun CookieImportDialog(
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var cookieInput by remember { mutableStateOf("") }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cookie_import_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.cookie_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    label = { Text("Cookie String") },
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (cookieInput.isNotBlank()) {
                        onConfirm(cookieInput)
                    }
                },
            ) {
                Text(stringResource(R.string.cookie_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
