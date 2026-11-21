package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Client
import com.example.data.ClientProject
import com.example.data.ClientTask
import com.example.data.PortalCrypto
import com.example.data.TaskPriority
import com.example.ui.theme.ClientColorPalette
import com.example.ui.theme.parseClientColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditorSheet(
    existingClient: Client?,
    existingClients: List<Client>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        company: String,
        notes: String,
        colorHex: String,
        portalEnabled: Boolean,
        portalEmail: String,
        portalPassword: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existingClient) { mutableStateOf(existingClient?.name.orEmpty()) }
    var company by remember(existingClient) { mutableStateOf(existingClient?.company.orEmpty()) }
    var notes by remember(existingClient) { mutableStateOf(existingClient?.notes.orEmpty()) }
    var colorHex by remember(existingClient) {
        mutableStateOf(existingClient?.colorHex ?: ClientColorPalette.first().hex)
    }
    var portalEnabled by remember(existingClient) { mutableStateOf(existingClient?.portalEnabled ?: false) }
    var portalEmail by remember(existingClient) { mutableStateOf(existingClient?.portalEmail.orEmpty()) }
    var portalPassword by remember(existingClient) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val isEditing = existingClient != null
    val normalizedEmail = PortalCrypto.normalizeEmail(portalEmail)
    val isEmailValid = PortalCrypto.isValidEmail(normalizedEmail)
    val isDuplicateEmail = portalEnabled && normalizedEmail.isNotBlank() && existingClients.any {
        it.id != existingClient?.id &&
            it.portalEnabled &&
            it.portalEmail.equals(normalizedEmail, ignoreCase = true)
    }

    val hadPortalWithSameEmail = isEditing &&
        existingClient?.portalEnabled == true &&
        PortalCrypto.isValidPortalKey(existingClient.portalKey) &&
        existingClient.portalEmail.equals(normalizedEmail, ignoreCase = true)

    val requiresPassword = portalEnabled && !hadPortalWithSameEmail
    val isPasswordValid = if (!portalEnabled) {
        true
    } else if (requiresPassword) {
        portalPassword.length >= PortalCrypto.MIN_PASSWORD_LENGTH
    } else {
        portalPassword.isEmpty() || portalPassword.length >= PortalCrypto.MIN_PASSWORD_LENGTH
    }

    val portalError: String? = when {
        !portalEnabled -> null
        normalizedEmail.isEmpty() -> null
        !isEmailValid -> "Enter a valid portal email address."
        isDuplicateEmail -> "This email is already assigned to another client."
        requiresPassword && portalPassword.isNotEmpty() && portalPassword.length < PortalCrypto.MIN_PASSWORD_LENGTH ->
            "Password must be at least ${PortalCrypto.MIN_PASSWORD_LENGTH} characters."
        !requiresPassword && portalPassword.isNotEmpty() && portalPassword.length < PortalCrypto.MIN_PASSWORD_LENGTH ->
            "New password must be at least ${PortalCrypto.MIN_PASSWORD_LENGTH} characters."
        else -> null
    }

    val canSave = name.trim().isNotEmpty() &&
        !isBusy &&
        (!portalEnabled || (isEmailValid && !isDuplicateEmail && isPasswordValid))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val previewColor = parseClientColor(colorHex)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = clientInitials(name),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Column {
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.sheet_edit_client_title else R.string.sheet_new_client_title
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!isEditing) {
                        Text(
                            text = stringResource(R.string.field_portal_default_project_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(name),
                label = { Text(stringResource(R.string.field_client_name)) },
                placeholder = { Text(stringResource(R.string.field_client_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("client_name_input")
            )

            OutlinedTextField(
                value = company,
                onValueChange = { if (it.length <= 80) company = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(company),
                label = { Text(stringResource(R.string.field_client_company)) },
                placeholder = { Text(stringResource(R.string.field_client_company_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("client_company_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 300) notes = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(notes),
                label = { Text(stringResource(R.string.field_client_notes)) },
                placeholder = { Text(stringResource(R.string.field_client_notes_placeholder)) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("client_notes_input")
            )

            ColorPaletteRow(
                label = stringResource(R.string.field_client_color),
                selectedHex = colorHex,
                onSelectHex = { colorHex = it }
            )

            // Client Portal Access Section
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (portalEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.field_portal_section_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.field_portal_section_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = portalEnabled,
                            onCheckedChange = { portalEnabled = it },
                            modifier = Modifier.testTag("client_portal_toggle")
                        )
                    }

                    AnimatedVisibility(visible = portalEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = portalEmail,
                                onValueChange = { if (it.length <= 160) portalEmail = it },
                                label = { Text(stringResource(R.string.field_portal_email)) },
                                placeholder = { Text(stringResource(R.string.field_portal_email_placeholder)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Email,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("client_portal_email_input")
                            )

                            OutlinedTextField(
                                value = portalPassword,
                                onValueChange = { if (it.length <= 128) portalPassword = it },
                                label = {
                                    Text(
                                        stringResource(
                                            if (hadPortalWithSameEmail) {
                                                R.string.field_portal_password_keep_hint
                                            } else {
                                                R.string.field_portal_password
                                            }
                                        )
                                    )
                                },
                                placeholder = {
                                    Text(
                                        stringResource(
                                            if (hadPortalWithSameEmail) {
                                                R.string.field_portal_password_keep_hint
                                            } else {
                                                R.string.field_portal_password_placeholder
                                            }
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showPassword = !showPassword },
                                        modifier = Modifier.testTag("client_portal_password_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (showPassword) {
                                                Icons.Rounded.VisibilityOff
                                            } else {
                                                Icons.Rounded.Visibility
                                            },
                                            contentDescription = stringResource(R.string.auth_toggle_password_cd)
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("client_portal_password_input")
                            )

                            if (portalError != null) {
                                Text(
                                    text = portalError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        onSubmit(
                            name,
                            company,
                            notes,
                            colorHex,
                            portalEnabled,
                            portalEmail,
                            portalPassword
                        )
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_client_button")
                ) {
                    Text(
                        stringResource(
                            if (isEditing) R.string.action_save else R.string.action_create
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorSheet(
    client: Client,
    existingProject: ClientProject?,
    canDelete: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, description: String, colorHex: String) -> Unit,
    onRequestDelete: ((ClientProject) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existingProject) { mutableStateOf(existingProject?.name.orEmpty()) }
    var description by remember(existingProject) { mutableStateOf(existingProject?.description.orEmpty()) }
    var colorHex by remember(existingProject, client) {
        mutableStateOf(existingProject?.colorHex ?: client.colorHex)
    }

    val isEditing = existingProject != null
    val canSave = name.trim().isNotEmpty() && !isBusy

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(parseClientColor(colorHex))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(
                                if (isEditing) R.string.sheet_edit_project_title else R.string.sheet_new_project_title
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.bodySmall.withBidi(client.name),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (existingProject != null && canDelete && onRequestDelete != null) {
                    TextButton(
                        onClick = { onRequestDelete(existingProject) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("delete_project_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(name),
                label = { Text(stringResource(R.string.field_project_name)) },
                placeholder = { Text(stringResource(R.string.field_project_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_name_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 300) description = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(description),
                label = { Text(stringResource(R.string.field_project_description)) },
                placeholder = { Text(stringResource(R.string.field_project_description_placeholder)) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_description_input")
            )

            ColorPaletteRow(
                label = stringResource(R.string.field_project_color),
                selectedHex = colorHex,
                onSelectHex = { colorHex = it }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onSubmit(name, description, colorHex) },
                    enabled = canSave,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_project_button")
                ) {
                    Text(
                        stringResource(
                            if (isEditing) R.string.action_save else R.string.action_create
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPaletteRow(
    label: String,
    selectedHex: String,
    onSelectHex: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            ClientColorPalette.forEach { swatch ->
                val selected = selectedHex.equals(swatch.hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(swatch.color.copy(alpha = if (selected) 0.16f else 0f))
                        .clickable(
                            role = Role.RadioButton,
                            onClickLabel = swatch.label
                        ) { onSelectHex(swatch.hex) }
                        .testTag("color_swatch_${swatch.label.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(swatch.color)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        2.5.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = swatch.label,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    clients: List<Client>,
    projectsByClient: Map<String, List<ClientProject>>,
    initialClientId: String?,
    initialProjectId: String?,
    existingTask: ClientTask?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        clientId: String,
        projectId: String,
        title: String,
        details: String,
        priority: TaskPriority,
        dueLabel: String,
        completed: Boolean
    ) -> Unit,
    onDelete: ((ClientTask) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = existingTask != null

    var selectedClientId by remember(existingTask, initialClientId, clients) {
        mutableStateOf(
            existingTask?.clientId
                ?: initialClientId?.takeIf { id -> clients.any { it.id == id } }
                ?: clients.firstOrNull()?.id.orEmpty()
        )
    }

    val availableProjects = remember(selectedClientId, projectsByClient) {
        projectsByClient[selectedClientId].orEmpty()
    }

    var selectedProjectId by remember(existingTask, initialProjectId, selectedClientId) {
        val projs = projectsByClient[selectedClientId].orEmpty()
        val candidate = existingTask?.projectId?.takeIf { id -> projs.any { it.id == id } }
            ?: initialProjectId?.takeIf { id -> projs.any { it.id == id } }
            ?: projs.firstOrNull { it.defaultProject }?.id
            ?: projs.firstOrNull()?.id
            ?: ""
        mutableStateOf(candidate)
    }

    LaunchedEffect(selectedClientId, availableProjects) {
        if (availableProjects.isNotEmpty() && availableProjects.none { it.id == selectedProjectId }) {
            selectedProjectId = availableProjects.firstOrNull { it.defaultProject }?.id
                ?: availableProjects.first().id
        }
    }

    var title by remember(existingTask) { mutableStateOf(existingTask?.title.orEmpty()) }
    var details by remember(existingTask) { mutableStateOf(existingTask?.details.orEmpty()) }
    var dueLabel by remember(existingTask) { mutableStateOf(existingTask?.dueLabel.orEmpty()) }
    var priority by remember(existingTask) {
        mutableStateOf(existingTask?.priorityEnum() ?: TaskPriority.NORMAL)
    }

    val canSave = title.trim().isNotEmpty() && selectedClientId.isNotBlank() && !isBusy

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (isEditing) R.string.sheet_edit_task_title else R.string.sheet_new_task_title
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (existingTask != null && onDelete != null) {
                    TextButton(
                        onClick = { onDelete(existingTask) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("delete_task_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }

            if (!isEditing && clients.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.field_task_client),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        clients.forEach { client ->
                            val isChosen = client.id == selectedClientId
                            val accent = parseClientColor(client.colorHex)
                            FilterChip(
                                selected = isChosen,
                                onClick = { selectedClientId = client.id },
                                label = {
                                    Text(
                                        text = client.name,
                                        style = MaterialTheme.typography.labelLarge.withBidi(client.name),
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.16f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            if (availableProjects.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.field_task_project),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        availableProjects.forEach { project ->
                            val isChosen = project.id == selectedProjectId
                            val accent = parseClientColor(project.colorHex)
                            FilterChip(
                                selected = isChosen,
                                onClick = { selectedProjectId = project.id },
                                label = {
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.labelLarge.withBidi(project.name),
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.16f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("task_project_chip_${project.id}")
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 140) title = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(title),
                label = { Text(stringResource(R.string.field_task_title)) },
                placeholder = { Text(stringResource(R.string.field_task_title_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input")
            )

            OutlinedTextField(
                value = details,
                onValueChange = { if (it.length <= 400) details = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(details),
                label = { Text(stringResource(R.string.field_task_details)) },
                placeholder = { Text(stringResource(R.string.field_task_details_placeholder)) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_details_input")
            )

            OutlinedTextField(
                value = dueLabel,
                onValueChange = { if (it.length <= 40) dueLabel = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(dueLabel),
                label = { Text(stringResource(R.string.field_task_due)) },
                placeholder = { Text(stringResource(R.string.field_task_due_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_due_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.field_task_priority),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TaskPriority.entries.forEach { option ->
                        val selected = priority == option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { priority = option }
                                .testTag("priority_${option.raw.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        onSubmit(
                            selectedClientId,
                            selectedProjectId,
                            title,
                            details,
                            priority,
                            dueLabel,
                            existingTask?.completed ?: false
                        )
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_task_button")
                ) {
                    Text(
                        stringResource(
                            if (isEditing) R.string.action_save else R.string.action_create
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteClientDialog(
    client: Client,
    projectCount: Int,
    taskCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.clients_delete_confirm_title, bidiIsolate(client.name)))
        },
        text = { Text(stringResource(R.string.clients_delete_confirm_body, projectCount, taskCount)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_client_button")
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun ConfirmDeleteProjectDialog(
    project: ClientProject,
    taskCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.project_delete_confirm_title, bidiIsolate(project.name)))
        },
        text = { Text(stringResource(R.string.project_delete_confirm_body, taskCount)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_project_button")
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun ConfirmSignOutDialog(
    titleRes: Int = R.string.auth_sign_out_confirm_title,
    bodyRes: Int = R.string.auth_sign_out_confirm_body,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_sign_out_button")
            ) {
                Text(stringResource(R.string.auth_sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
