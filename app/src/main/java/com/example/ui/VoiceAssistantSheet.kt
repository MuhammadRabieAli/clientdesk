package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.ProposedClient
import com.example.ai.ProposedTask
import com.example.ai.ProposedTaskAction
import com.example.ai.VoiceAssistantPlan
import com.example.ai.VoiceAssistantState
import com.example.ai.VoiceAudioRecorder
import com.example.ai.VoiceSpeaker
import com.example.ai.VoiceTaskActionType
import com.example.data.Client
import com.example.data.TaskPriority
import com.example.ui.theme.StudioCrimson
import com.example.ui.theme.StudioForest
import com.example.ui.theme.StudioTerracotta
import com.example.ui.theme.parseClientColor
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantSheet(
    clients: List<Client>,
    selectedClientId: String?,
    voiceState: VoiceAssistantState,
    autoApply: Boolean,
    isBusy: Boolean,
    onSetAutoApply: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSendAudio: (audioWavBytes: ByteArray, preferredClientId: String?) -> Unit,
    onSendTextCommand: (command: String, preferredClientId: String?) -> Unit,
    onRetryLast: () -> Unit,
    onCancelProcessing: () -> Unit,
    onResetVoice: () -> Unit,
    onApplyPlan: (
        plan: VoiceAssistantPlan,
        clients: List<ProposedClient>,
        tasks: List<ProposedTask>,
        taskActions: List<ProposedTaskAction>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val recorder = remember { VoiceAudioRecorder() }
    val speaker = remember(context) { VoiceSpeaker(context) }

    val isRecording by recorder.isRecording.collectAsStateWithLifecycle()
    val liveAmplitude by recorder.amplitude.collectAsStateWithLifecycle()
    val waveformLevels by recorder.waveformLevels.collectAsStateWithLifecycle()
    val elapsedMillis by recorder.elapsedMillis.collectAsStateWithLifecycle()

    var speakerEnabled by remember { mutableStateOf(true) }
    var typedCommand by remember { mutableStateOf("") }
    var localMicError by remember { mutableStateOf<String?>(null) }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val tooShortMessage = stringResource(R.string.voice_recording_too_short)
    val genericMicError = stringResource(R.string.voice_recording_error)

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun startRecordingNow() {
        localMicError = null
        speaker.stop()
        if (voiceState !is VoiceAssistantState.Idle) {
            onResetVoice()
        }
        val started = recorder.start(
            scope = scope,
            onMaxDurationReached = { wavBytes ->
                onSendAudio(wavBytes, selectedClientId)
            },
            onError = { err ->
                localMicError = err.ifBlank { genericMicError }
            }
        )
        if (!started && localMicError == null) {
            localMicError = genericMicError
        }
    }

    fun stopRecordingAndAnalyze() {
        scope.launch {
            val wavBytes = recorder.stopAndEncodeWav()
            if (wavBytes == null) {
                localMicError = tooShortMessage
            } else {
                localMicError = null
                onSendAudio(wavBytes, selectedClientId)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            permissionDeniedOnce = false
            localMicError = null
            startRecordingNow()
        } else {
            permissionDeniedOnce = true
        }
    }

    fun handleMicOrbTap() {
        if (voiceState is VoiceAssistantState.Processing || isBusy) return
        if (isRecording) {
            stopRecordingAndAnalyze()
        } else if (hasMicPermission) {
            startRecordingNow()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Automatically speak Gemini's concise voice reply when a plan or answer arrives
    LaunchedEffect(voiceState, speakerEnabled) {
        if (!speakerEnabled) return@LaunchedEffect
        when (voiceState) {
            is VoiceAssistantState.Review -> speaker.speak(voiceState.plan.spokenReply)
            is VoiceAssistantState.Executed -> speaker.speak(voiceState.spokenReply)
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            speaker.release()
        }
    }

    val activeClientName = remember(clients, selectedClientId) {
        clients.firstOrNull { it.id == selectedClientId }?.name
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (voiceState !is VoiceAssistantState.Processing && !isBusy) {
                recorder.cancel()
                speaker.stop()
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
            // Sheet Header with Speaker & Auto-Apply Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.voice_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (activeClientName != null) {
                            "Context: ${bidiIsolate(activeClientName)}"
                        } else {
                            stringResource(R.string.voice_sheet_subtitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Spoken Reply Mute/Unmute
                IconButton(
                    onClick = {
                        speakerEnabled = !speakerEnabled
                        if (!speakerEnabled) speaker.stop()
                    },
                    modifier = Modifier.testTag("voice_speaker_toggle")
                ) {
                    Icon(
                        imageVector = if (speakerEnabled) {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        },
                        contentDescription = stringResource(
                            if (speakerEnabled) R.string.voice_speaker_on_cd else R.string.voice_speaker_off_cd
                        ),
                        tint = if (speakerEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Compact Auto-apply Toggle Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = autoApply,
                    onClick = { onSetAutoApply(!autoApply) },
                    label = { Text(stringResource(R.string.voice_auto_apply_label)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("voice_auto_apply_chip")
                )

                Text(
                    text = if (autoApply) {
                        "Adds & check-offs apply instantly"
                    } else {
                        "Review before applying"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Error Banner (mic or Gemini) with one-tap Retry when recording is cached
            val errorState = voiceState as? VoiceAssistantState.Error
            val errorText = localMicError ?: errorState?.message
            val canRetryNow = localMicError == null && errorState?.canRetry == true
            if (!errorText.isNullOrBlank()) {
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
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (canRetryNow) {
                            OutlinedButton(
                                onClick = onRetryLast,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("voice_error_retry_button")
                            ) {
                                Text(stringResource(R.string.error_retry))
                            }
                        }
                    }
                }
            }

            when (voiceState) {
                is VoiceAssistantState.Processing -> {
                    VoiceProcessingCard(
                        statusMessage = voiceState.statusMessage,
                        onCancel = onCancelProcessing
                    )
                }

                is VoiceAssistantState.Review -> {
                    VoiceReviewSection(
                        plan = voiceState.plan,
                        existingClients = clients,
                        isBusy = isBusy,
                        onReplayReply = { speaker.speak(voiceState.plan.spokenReply) },
                        onSpeakAgain = {
                            onResetVoice()
                            handleMicOrbTap()
                        },
                        onConfirm = { draftClients, draftTasks, draftActions ->
                            onApplyPlan(voiceState.plan, draftClients, draftTasks, draftActions)
                        }
                    )
                }

                is VoiceAssistantState.Executed -> {
                    VoiceExecutedCard(
                        transcript = voiceState.transcript,
                        spokenReply = voiceState.spokenReply,
                        executedSummary = voiceState.executedSummary,
                        onReplayReply = { speaker.speak(voiceState.spokenReply) },
                        onSpeakAgain = {
                            onResetVoice()
                            handleMicOrbTap()
                        },
                        onDone = {
                            speaker.stop()
                            onDismiss()
                        }
                    )
                }

                is VoiceAssistantState.Idle, is VoiceAssistantState.Error -> {
                    // Microphone Permission Rationale Card when denied
                    AnimatedVisibility(visible = permissionDeniedOnce && !hasMicPermission) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MicOff,
                                        contentDescription = null,
                                        tint = StudioTerracotta,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.voice_permission_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.voice_permission_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("voice_grant_permission_button")
                                ) {
                                    Text(stringResource(R.string.voice_permission_grant))
                                }
                            }
                        }
                    }

                    // Hero Interactive Voice Orb & Live Waveform Studio Card
                    VoiceCaptureStage(
                        isRecording = isRecording,
                        amplitude = liveAmplitude,
                        waveformLevels = waveformLevels,
                        elapsedMillis = elapsedMillis,
                        onOrbClick = { handleMicOrbTap() },
                        onCancelRecording = {
                            recorder.cancel()
                            localMicError = null
                        }
                    )

                    // Quick Voice Example Chips (tap to try or as spoken prompts)
                    if (!isRecording) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            VoiceSuggestionChip(
                                label = stringResource(R.string.voice_example_add),
                                onClick = {
                                    onSendTextCommand(
                                        "Add a new client Horizon Labs for Mobile Redesign and add two tasks: Prepare wireframes high priority due tomorrow, and Send project timeline.",
                                        selectedClientId
                                    )
                                }
                            )
                            VoiceSuggestionChip(
                                label = stringResource(R.string.voice_example_ask),
                                onClick = {
                                    onSendTextCommand(
                                        "What open high-priority tasks do I have right now?",
                                        selectedClientId
                                    )
                                }
                            )
                            VoiceSuggestionChip(
                                label = stringResource(R.string.voice_example_complete),
                                onClick = {
                                    typedCommand = "Mark the first task as done"
                                }
                            )
                            VoiceSuggestionChip(
                                label = stringResource(R.string.voice_example_arabic),
                                onClick = {
                                    onSendTextCommand(
                                        "أضف عميل جديد استوديو أفق وأضف مهمة تصميم الشعار عاجل غداً ومهمة إرسال الفاتورة.",
                                        selectedClientId
                                    )
                                }
                            )
                        }

                        // Compact Typed Command Fallback
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = typedCommand,
                                onValueChange = { if (it.length <= 400) typedCommand = it },
                                textStyle = MaterialTheme.typography.bodyMedium.withBidi(typedCommand),
                                label = { Text(stringResource(R.string.voice_type_fallback_label)) },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.voice_type_fallback_placeholder),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (typedCommand.isNotBlank()) {
                                            val cmd = typedCommand.trim()
                                            typedCommand = ""
                                            onSendTextCommand(cmd, selectedClientId)
                                        }
                                    }
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("voice_typed_command_input")
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (typedCommand.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(52.dp)
                            ) {
                                IconButton(
                                    enabled = typedCommand.isNotBlank(),
                                    onClick = {
                                        val cmd = typedCommand.trim()
                                        typedCommand = ""
                                        onSendTextCommand(cmd, selectedClientId)
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("voice_send_typed_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = stringResource(R.string.voice_send_command_cd),
                                        tint = if (typedCommand.isNotBlank()) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
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

@Composable
private fun VoiceCaptureStage(
    isRecording: Boolean,
    amplitude: Float,
    waveformLevels: List<Float>,
    elapsedMillis: Long,
    onOrbClick: () -> Unit,
    onCancelRecording: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.tertiary
    val surfaceBorder = MaterialTheme.colorScheme.outline

    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb_pulse")
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breath"
    )
    val smoothAmplitude by animateFloatAsState(
        targetValue = if (isRecording) amplitude else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "live_amplitude"
    )

    val activeColor = if (isRecording) accentColor else primaryColor

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            width = if (isRecording) 1.5.dp else 1.dp,
            color = if (isRecording) accentColor.copy(alpha = 0.55f) else surfaceBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            // Interactive Voice Orb with Reactive Radial Aura
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(136.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.34f
                    val outerRadius = if (isRecording) {
                        baseRadius * (1.28f + smoothAmplitude * 0.62f)
                    } else {
                        baseRadius * (1.14f * idleBreath)
                    }
                    val midRadius = if (isRecording) {
                        baseRadius * (1.12f + smoothAmplitude * 0.34f)
                    } else {
                        baseRadius * (1.05f * idleBreath)
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = if (isRecording) 0.30f else 0.16f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outerRadius
                        ),
                        radius = outerRadius,
                        center = center
                    )

                    drawCircle(
                        color = activeColor.copy(alpha = if (isRecording) 0.22f else 0.12f),
                        radius = midRadius,
                        center = center
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = activeColor,
                    shadowElevation = if (isRecording) 8.dp else 4.dp,
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = onOrbClick
                        )
                        .testTag("voice_mic_orb_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = stringResource(
                                if (isRecording) R.string.voice_tap_to_stop else R.string.voice_tap_to_speak
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            // Live Waveform Bars while Recording
            AnimatedVisibility(visible = isRecording) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .height(34.dp)
                    ) {
                        val count = waveformLevels.size.coerceAtLeast(1)
                        val gap = 4.dp.toPx()
                        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(3.dp.toPx())
                        waveformLevels.forEachIndexed { idx, level ->
                            val barHeight = (size.height * level.coerceIn(0.12f, 1f))
                            val left = idx * (barWidth + gap)
                            val top = (size.height - barHeight) / 2f
                            drawRoundRect(
                                color = accentColor.copy(alpha = 0.45f + level * 0.55f),
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val totalSeconds = (elapsedMillis / 1000L).toInt()
                        val mins = totalSeconds / 60
                        val secs = totalSeconds % 60
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", mins, secs),
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor
                        )
                        OutlinedButton(
                            onClick = onCancelRecording,
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier.testTag("voice_cancel_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.voice_cancel_recording))
                        }
                    }
                }
            }

            // Status Caption
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isRecording) R.string.voice_listening_title else R.string.voice_tap_to_speak
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isRecording) {
                        stringResource(R.string.voice_tap_to_stop)
                    } else {
                        "English & العربية · Clients, tasks, check-offs, questions"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceSuggestionChip(
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
private fun VoiceProcessingCard(
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
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("voice_cancel_processing_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun VoiceExecutedCard(
    transcript: String,
    spokenReply: String,
    executedSummary: String,
    onReplayReply: () -> Unit,
    onSpeakAgain: () -> Unit,
    onDone: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (transcript.isNotBlank()) {
            TranscriptBubble(transcript = transcript)
        }

        AssistantReplyBanner(
            reply = spokenReply,
            badgeText = executedSummary.ifBlank { null },
            onReplay = onReplayReply
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            OutlinedButton(
                onClick = onSpeakAgain,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("voice_speak_again_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.voice_speak_again), maxLines = 1)
            }

            Button(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("voice_done_button")
            ) {
                Text(stringResource(R.string.voice_done_button), maxLines = 1)
            }
        }
    }
}

@Composable
private fun VoiceReviewSection(
    plan: VoiceAssistantPlan,
    existingClients: List<Client>,
    isBusy: Boolean,
    onReplayReply: () -> Unit,
    onSpeakAgain: () -> Unit,
    onConfirm: (List<ProposedClient>, List<ProposedTask>, List<ProposedTaskAction>) -> Unit
) {
    var draftClients by remember(plan) { mutableStateOf(plan.clients) }
    var draftTasks by remember(plan) { mutableStateOf(plan.tasks) }
    var draftActions by remember(plan) { mutableStateOf(plan.taskActions) }

    LaunchedEffect(plan) {
        draftClients = plan.clients
        draftTasks = plan.tasks
        draftActions = plan.taskActions
    }

    val allCandidateClientNames = remember(existingClients, draftClients) {
        (existingClients.map { it.name } + draftClients.map { it.name })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    val selectedClientsCount = draftClients.count { it.selected }
    val selectedTasksCount = draftTasks.count { it.selected }
    val selectedActionsCount = draftActions.count { it.selected }
    val totalSelected = selectedClientsCount + selectedTasksCount + selectedActionsCount

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (plan.transcript.isNotBlank()) {
            TranscriptBubble(transcript = plan.transcript)
        }

        AssistantReplyBanner(
            reply = plan.spokenReply,
            badgeText = null,
            onReplay = onReplayReply
        )

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
                            }
                        }
                    }
                }
            }
        }

        // Proposed New Tasks
        if (draftTasks.isNotEmpty()) {
            Text(
                text = stringResource(R.string.ai_review_tasks_header, selectedTasksCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        6.dp,
                                        if (isTaskRtl) Alignment.End else Alignment.Start
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
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
                                                maxLines = 1
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

        // Proposed Existing Task Updates (Complete / Reopen / Update / Delete)
        if (draftActions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.voice_review_updates_header, selectedActionsCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draftActions.forEachIndexed { idx, action ->
                    val (badgeColor, badgeLabel, badgeIcon) = when (action.actionType) {
                        VoiceTaskActionType.COMPLETE -> Triple(
                            StudioForest,
                            stringResource(R.string.voice_action_complete),
                            Icons.Rounded.CheckCircle
                        )
                        VoiceTaskActionType.REOPEN -> Triple(
                            MaterialTheme.colorScheme.primary,
                            stringResource(R.string.voice_action_reopen),
                            Icons.Rounded.Refresh
                        )
                        VoiceTaskActionType.UPDATE -> Triple(
                            StudioTerracotta,
                            stringResource(R.string.voice_action_update),
                            Icons.Rounded.Edit
                        )
                        VoiceTaskActionType.DELETE -> Triple(
                            StudioCrimson,
                            stringResource(R.string.voice_action_delete),
                            Icons.Rounded.DeleteOutline
                        )
                    }
                    val displayTitle = action.newTitle ?: action.existingTask.title

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(
                            1.dp,
                            if (action.actionType == VoiceTaskActionType.DELETE) {
                                StudioCrimson.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                draftActions = draftActions.toMutableList().also {
                                    it[idx] = action.copy(selected = !action.selected)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = action.selected,
                                onCheckedChange = { checked ->
                                    draftActions = draftActions.toMutableList().also {
                                        it[idx] = action.copy(selected = checked)
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
                                    text = displayTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ).withBidi(displayTitle),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = badgeColor.copy(alpha = 0.14f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = badgeIcon,
                                                contentDescription = null,
                                                tint = badgeColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = badgeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeColor
                                            )
                                        }
                                    }

                                    Text(
                                        text = action.clientName,
                                        style = MaterialTheme.typography.labelSmall.withBidi(action.clientName),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (action.newDueLabel != null) {
                                        Text(
                                            text = "· ${action.newDueLabel}",
                                            style = MaterialTheme.typography.labelSmall.withBidi(action.newDueLabel),
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

        // Review Footer Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            OutlinedButton(
                onClick = onSpeakAgain,
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("voice_review_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.voice_speak_again), maxLines = 1)
            }

            Button(
                onClick = { onConfirm(draftClients, draftTasks, draftActions) },
                enabled = totalSelected > 0 && !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.4f)
                    .height(50.dp)
                    .testTag("voice_confirm_apply_button")
            ) {
                Text(
                    text = if (totalSelected > 0) {
                        "${stringResource(R.string.voice_apply_actions)} ($totalSelected)"
                    } else {
                        stringResource(R.string.voice_apply_actions)
                    },
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TranscriptBubble(transcript: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.voice_you_said_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "“$transcript”",
                style = MaterialTheme.typography.bodyMedium.withBidi(transcript),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AssistantReplyBanner(
    reply: String,
    badgeText: String?,
    onReplay: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = reply,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        .withBidi(reply),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!badgeText.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = StudioForest,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioForest
                        )
                    }
                }
            }
            IconButton(
                onClick = onReplay,
                modifier = Modifier.testTag("voice_replay_reply_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = stringResource(R.string.voice_replay_reply_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
