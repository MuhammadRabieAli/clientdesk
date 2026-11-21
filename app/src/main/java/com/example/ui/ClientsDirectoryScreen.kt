package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Client
import com.example.data.ClientProject
import com.example.ui.theme.parseClientColor

private val DirectoryCardShape = RoundedCornerShape(20.dp)
private val AvatarShape = RoundedCornerShape(14.dp)
private val ActionButtonShape = RoundedCornerShape(12.dp)

@Immutable
private data class PreparedDirectoryClient(
    val client: Client,
    val projects: List<ClientProject>,
    val initials: String,
    val accent: Color,
    val isNameRtl: Boolean,
    val isCompanyRtl: Boolean,
    val isNotesRtl: Boolean,
    val openBadgeText: String,
    val doneProgressText: String,
    val projectCountText: String,
    val progress: Float
)

@Composable
fun ClientsDirectoryScreen(
    workspace: WorkspaceData,
    onOpenClientTasks: (Client, String?) -> Unit,
    onRegisterClient: () -> Unit,
    onAddProjectForClient: (Client) -> Unit,
    onAddTaskForClient: (Client) -> Unit,
    onEditClient: (Client) -> Unit,
    onRequestDeleteClient: (Client) -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val preparedClients = remember(workspace, configuration) {
        workspace.clients.map { client ->
            val projects = workspace.projectsByClient[client.id].orEmpty()
            val totalCount = workspace.preparedByClient[client.id]?.size ?: 0
            val openCount = workspace.openCountByClient[client.id] ?: 0
            val doneCount = totalCount - openCount
            val projectCountText = if (projects.size == 1) {
                resources.getString(R.string.workspace_project_count_one)
            } else {
                resources.getString(R.string.workspace_project_count_many, projects.size)
            }
            PreparedDirectoryClient(
                client = client,
                projects = projects,
                initials = clientInitials(client.name),
                accent = workspace.clientAccentById[client.id] ?: parseClientColor(client.colorHex),
                isNameRtl = workspace.clientNameRtlById[client.id] ?: false,
                isCompanyRtl = if (client.company.isNotBlank()) client.company.isRtlText() else false,
                isNotesRtl = if (client.notes.isNotBlank()) client.notes.isRtlText() else false,
                openBadgeText = resources.getString(R.string.clients_open_tasks_badge, openCount),
                doneProgressText = resources.getString(R.string.clients_done_progress, doneCount, totalCount),
                projectCountText = projectCountText,
                progress = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()
            )
        }
    }
    val totalOpen = workspace.totalOpen

    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val cardRipple = remember { ripple(bounded = true) }
    val titleLargeLtr = remember(typography) { typography.titleLarge.withBidi(false) }
    val titleLargeRtl = remember(typography) { typography.titleLarge.withBidi(true) }
    val bodySmallLtr = remember(typography) { typography.bodySmall.withBidi(false) }
    val bodySmallRtl = remember(typography) { typography.bodySmall.withBidi(true) }
    val initialsStyle = remember(typography) { typography.titleMedium.copy(fontWeight = FontWeight.Bold) }
    val cardBgColor = colorScheme.surfaceContainerLowest
    val cardOutlineColor = colorScheme.outline

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "clients_header", contentType = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.clients_title),
                            style = typography.headlineMedium,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(
                                R.string.clients_subtitle,
                                preparedClients.size,
                                totalOpen
                            ),
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onRegisterClient,
                        shape = AvatarShape,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("directory_register_client_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.clients_register_button))
                    }
                }
            }

            if (preparedClients.isEmpty()) {
                item(key = "clients_empty", contentType = "empty") {
                    Surface(
                        shape = DirectoryCardShape,
                        color = colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.workspace_empty_clients_title),
                                style = typography.titleMedium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.workspace_empty_clients_body),
                                style = typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = preparedClients,
                    key = { it.client.id },
                    contentType = { "client_card" }
                ) { item ->
                    val client = item.client
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithCache {
                                val r = 20.dp.toPx()
                                val cornerRadius = CornerRadius(r, r)
                                val strokePx = 1.dp.toPx()
                                val half = strokePx / 2f
                                val stroke = Stroke(strokePx)
                                val strokeTopLeft = Offset(half, half)
                                val strokeSize = Size(size.width - strokePx, size.height - strokePx)
                                onDrawBehind {
                                    drawRoundRect(cardBgColor, cornerRadius = cornerRadius)
                                    drawRoundRect(
                                        color = cardOutlineColor,
                                        topLeft = strokeTopLeft,
                                        size = strokeSize,
                                        cornerRadius = cornerRadius,
                                        style = stroke
                                    )
                                }
                            }
                            .clickable(
                                interactionSource = null,
                                indication = cardRipple,
                                onClick = { onOpenClientTasks(client, null) }
                            )
                            .padding(18.dp)
                            .testTag("client_card_${client.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(item.accent, AvatarShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.initials,
                                        style = initialsStyle,
                                        color = Color.White
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = client.name,
                                        style = if (item.isNameRtl) titleLargeRtl else titleLargeLtr,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (client.company.isNotBlank()) {
                                        Text(
                                            text = client.company,
                                            style = if (item.isCompanyRtl) bodySmallRtl else bodySmallLtr,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = { onAddProjectForClient(client) },
                                    modifier = Modifier.testTag("add_project_client_${client.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CreateNewFolder,
                                        contentDescription = stringResource(R.string.clients_add_project_action),
                                        tint = item.accent
                                    )
                                }
                                IconButton(
                                    onClick = { onEditClient(client) },
                                    modifier = Modifier.testTag("edit_client_${client.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.clients_edit_action),
                                        tint = colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onRequestDeleteClient(client) },
                                    modifier = Modifier.testTag("delete_client_${client.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.clients_delete_action),
                                        tint = colorScheme.error
                                    )
                                }
                            }
                        }

                        // Metadata Pills: Project count + Client Portal status
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.surfaceVariant
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = item.projectCountText,
                                        style = typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (client.portalEnabled) {
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.primaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LockOpen,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.clients_portal_active_badge),
                                            style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Quick-jump Project Chips
                        if (item.projects.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                item.projects.forEach { project ->
                                    val projAccent = workspace.projectAccentById[project.id]
                                        ?: parseClientColor(project.colorHex)
                                    val projOpen = workspace.openCountByProject[project.id] ?: 0
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = projAccent.copy(alpha = 0.11f),
                                        border = BorderStroke(1.dp, projAccent.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onOpenClientTasks(client, project.id) }
                                            .testTag("directory_project_chip_${project.id}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(projAccent)
                                            )
                                            Text(
                                                text = project.name,
                                                style = typography.labelMedium.withBidi(project.name),
                                                color = colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = projOpen.toString(),
                                                style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = projAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (client.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = client.notes,
                                style = if (item.isNotesRtl) bodySmallRtl else bodySmallLtr,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.openBadgeText,
                                style = typography.labelMedium,
                                color = item.accent
                            )
                            Text(
                                text = item.doneProgressText,
                                style = typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { item.progress },
                            color = item.accent,
                            trackColor = colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onAddTaskForClient(client) },
                                shape = ActionButtonShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("client_card_add_task_${client.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.workspace_add_task))
                            }

                            Button(
                                onClick = { onOpenClientTasks(client, null) },
                                shape = ActionButtonShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(stringResource(R.string.nav_tasks))
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }
}
