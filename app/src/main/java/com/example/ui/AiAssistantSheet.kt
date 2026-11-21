package com.example.ui

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.ai.AiAssistantState
import com.example.ai.AiWorkspacePlan
import com.example.ai.GeminiWorkspaceAssistant
import com.example.ai.ProposedClient
import com.example.ai.ProposedTask
import com.example.data.Client
import com.example.data.TaskPriority
import com.example.ui.theme.StudioTerracotta
import com.example.ui.theme.parseClientColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    clients: List<Client>,
    selectedClientId: String?,
    aiState: AiAssistantState,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onAnalyze: (prompt: String, image: Bitmap?, preferredClientId: String?) -> Unit,
    onCancelAnalyze: () -> Unit,
    onResetAi: () -> Unit,
    onApplyPlan: (List<ProposedClient>, List<ProposedTask>) -> Unit,
    onShowMessage: (String) -> Unit,
    onSwitchToVoice: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var prompt by remember { mutableStateOf("") }
    var targetClientId by remember(selectedClientId) { mutableStateOf(selectedClientId) }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isDecodingImage by remember { mutableStateOf(false) }

    val imageLoadError = stringResource(R.string.ai_image_load_error)
    val cameraUnavailableError = stringResource(R.string.ai_camera_unavailable)

    fun loadUriBitmap(uri: Uri) {
        scope.launch {
            isDecodingImage = true
            val decoded = GeminiWorkspaceAssistant.decodeScaledBitmap(context, uri)
            isDecodingImage = false
            if (decoded != null) {
                attachedBitmap = decoded
            } else {
                onShowMessage(imageLoadError)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            loadUriBitmap(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            loadUriBitmap(uri)
        }
    }

    fun launchCameraScan() {
        try {
            val file = GeminiWorkspaceAssistant.createTempScanFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            onShowMessage(cameraUnavailableError)
        } catch (_: Exception) {
            onShowMessage(cameraUnavailableError)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (aiState !is AiAssistantState.Loading && !isBusy) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ai_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onSwitchToVoice,
                    enabled = aiState !is AiAssistantState.Loading && !isBusy,
                    modifier = Modifier.testTag("ai_switch_to_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = stringResource(R.string.voice_switch_from_ai),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            when (aiState) {
                is AiAssistantState.Loading -> {
                    AiLoadingCard(
                        attachedBitmap = attachedBitmap,
                        statusMessage = aiState.statusMessage,
                        onCancel = onCancelAnalyze
                    )
                }

                is AiAssistantState.Review -> {
                    AiReviewSection(
                        plan = aiState.plan,
                        existingClients = clients,
                        isBusy = isBusy,
                        onStartOver = { onResetAi() },
                        onConfirm = { selectedClients, selectedTasks ->
                            onApplyPlan(selectedClients, selectedTasks)
                        }
                    )
                }

                is AiAssistantState.Applied -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = aiState.summary,
                                style = MaterialTheme.typography.bodyMedium.withBidi(aiState.summary),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { onResetAi() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.voice_done_button))
                            }
                        }
                    }
                }

                is AiAssistantState.Idle, is AiAssistantState.Error -> {
                    if (aiState is AiAssistantState.Error) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = aiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Optional Target Client Selector
                    if (clients.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.ai_target_client_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                FilterChip(
                                    selected = targetClientId == null,
                                    onClick = { targetClientId = null },
                                    label = { Text(stringResource(R.string.ai_target_auto)) },
                                    modifier = Modifier.testTag("ai_target_auto")
                                )
                                clients.forEach { client ->
                                    val accent = parseClientColor(client.colorHex)
                                    val isSelected = targetClientId == client.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { targetClientId = client.id },
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

                    // Image Scan Controls (Zero-permission Photo Picker + Camera Scan)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ai_pick_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_pick_photo), maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { launchCameraScan() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ai_camera_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_scan_camera), maxLines = 1)
                        }
                    }

                    // Attached Image Preview
                    if (isDecodingImage) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Preparing image…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (attachedBitmap != null) {
                        val previewBitmap = attachedBitmap!!
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.ai_attached_photo_cd),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                ) {
                                    IconButton(
                                        onClick = { attachedBitmap = null },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("ai_remove_photo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.ai_remove_photo),
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Natural Language Prompt Field (English & Arabic RTL-aware)
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { if (it.length <= 800) prompt = it },
                        textStyle = MaterialTheme.typography.bodyLarge.withBidi(prompt),
                        label = { Text(stringResource(R.string.ai_prompt_label)) },
                        placeholder = { Text(stringResource(R.string.ai_prompt_placeholder)) },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_prompt_input")
                    )

                    // Quick Prompt Starters
                    if (prompt.isBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            QuickPromptChip(
                                label = stringResource(R.string.ai_quick_chip_client),
                                onClick = {
                                    prompt = "Register client Northstar Studio for Brand Refresh and add 3 tasks: Audit visual identity (high priority, due Fri), Design logo options, Send invoice."
                                }
                            )
                            QuickPromptChip(
                                label = stringResource(R.string.ai_quick_chip_scan),
                                onClick = {
                                    prompt = "Extract all action items, priorities, and deadlines from this image."
                                }
                            )
                            QuickPromptChip(
                                label = stringResource(R.string.ai_quick_chip_arabic),
                                onClick = {
                                    prompt = "أضف عميل جديد شركة النور وأضف مهام: تصميم الهوية البصرية عاجل، مراجعة المحتوى، تسليم العرض يوم الأحد."
                                }
                            )
                        }
                    }

                    // Primary Analyze Action
                    val canAnalyze = (prompt.isNotBlank() || attachedBitmap != null) && !isDecodingImage
                    Button(
                        onClick = { onAnalyze(prompt, attachedBitmap, targetClientId) },
                        enabled = canAnalyze,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("ai_analyze_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_analyze_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPromptChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.withBidi(label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AiLoadingCard(
    attachedBitmap: Bitmap?,
    statusMessage: String,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            if (attachedBitmap != null) {
                Image(
                    bitmap = attachedBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 120.dp, height = 80.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = statusMessage.ifBlank { stringResource(R.string.ai_analyzing_title) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.ai_analyzing_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("ai_cancel_analysis_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun AiReviewSection(
    plan: AiWorkspacePlan,
    existingClients: List<Client>,
    isBusy: Boolean,
    onStartOver: () -> Unit,
    onConfirm: (List<ProposedClient>, List<ProposedTask>) -> Unit
) {
    var draftClients by remember(plan) { mutableStateOf(plan.clients) }
    var draftTasks by remember(plan) { mutableStateOf(plan.tasks) }

    LaunchedEffect(plan) {
        draftClients = plan.clients
        draftTasks = plan.tasks
    }

    val allCandidateClientNames = remember(existingClients, draftClients) {
        (existingClients.map { it.name } + draftClients.map { it.name })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    val selectedClientsCount = draftClients.count { it.selected }
    val selectedTasksCount = draftTasks.count { it.selected }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Gemini Summary Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = plan.summary,
                style = MaterialTheme.typography.bodyMedium.withBidi(plan.summary),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            )
        }

        // Proposed New Clients
        if (draftClients.isNotEmpty()) {
            Text(
                text = stringResource(R.string.ai_review_clients_header, selectedClientsCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draftClients.forEachIndexed { idx, client ->
                    val accent = parseClientColor(client.colorHex)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                draftClients = draftClients.toMutableList().also {
                                    it[idx] = client.copy(selected = !client.selected)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = client.selected,
                                onCheckedChange = { checked ->
                                    draftClients = draftClients.toMutableList().also {
                                        it[idx] = client.copy(selected = checked)
                                    }
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = client.name,
                                    style = MaterialTheme.typography.titleSmall.withBidi(client.name),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (client.company.isNotBlank()) {
                                    Text(
                                        text = client.company,
                                        style = MaterialTheme.typography.bodySmall.withBidi(client.company),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (client.notes.isNotBlank()) {
                                    Text(
                                        text = client.notes,
                                        style = MaterialTheme.typography.bodySmall.withBidi(client.notes),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Proposed Tasks
        if (draftTasks.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.ai_review_tasks_header, selectedTasksCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (allCandidateClientNames.size > 1) {
                    Text(
                        text = stringResource(R.string.ai_tap_switch_client_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draftTasks.forEachIndexed { idx, task ->
                    val isTaskRtl = task.title.isRtlText()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                draftTasks = draftTasks.toMutableList().also {
                                    it[idx] = task.copy(selected = !task.selected)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = task.selected,
                                onCheckedChange = { checked ->
                                    draftTasks = draftTasks.toMutableList().also {
                                        it[idx] = task.copy(selected = checked)
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 6.dp, end = 8.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ).withBidi(task.title),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (task.details.isNotBlank()) {
                                    Text(
                                        text = task.details,
                                        style = MaterialTheme.typography.bodySmall.withBidi(task.details),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        6.dp,
                                        if (isTaskRtl) Alignment.End else Alignment.Start
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Tappable Client Assignment Pill (cycles through clients)
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .clickable(enabled = allCandidateClientNames.size > 1) {
                                                val currentPos = allCandidateClientNames.indexOfFirst {
                                                    it.equals(task.clientName, ignoreCase = true)
                                                }
                                                val nextName = allCandidateClientNames[
                                                    (currentPos + 1) % allCandidateClientNames.size
                                                ]
                                                draftTasks = draftTasks.toMutableList().also {
                                                    it[idx] = task.copy(clientName = nextName)
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = task.clientName,
                                                style = MaterialTheme.typography.labelSmall.withBidi(task.clientName),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (allCandidateClientNames.size > 1) {
                                                Icon(
                                                    imageVector = Icons.Rounded.SwapHoriz,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (task.priority == TaskPriority.HIGH) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Flag,
                                                contentDescription = null,
                                                tint = StudioTerracotta,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.priority_high),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StudioTerracotta
                                            )
                                        }
                                    }

                                    if (task.dueLabel.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = task.dueLabel,
                                                style = MaterialTheme.typography.labelSmall.withBidi(task.dueLabel),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Review Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            OutlinedButton(
                onClick = onStartOver,
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("ai_start_over_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ai_start_over), maxLines = 1)
            }

            val totalChecked = selectedClientsCount + selectedTasksCount
            Button(
                onClick = { onConfirm(draftClients, draftTasks) },
                enabled = totalChecked > 0 && !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.45f)
                    .height(50.dp)
                    .testTag("ai_confirm_add_button")
            ) {
                Text(
                    text = if (totalChecked > 0) {
                        "${stringResource(R.string.ai_confirm_add)} ($totalChecked)"
                    } else {
                        stringResource(R.string.ai_confirm_add)
                    },
                    maxLines = 1
                )
            }
        }
    }
}
