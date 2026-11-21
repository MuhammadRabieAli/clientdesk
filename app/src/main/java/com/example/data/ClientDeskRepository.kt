package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ClientDeskRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        return uid
    }

    // --- OWNER REAL-TIME STREAMS ---

    fun observeClients(userId: String): Flow<List<Client>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/clients"
        emitAll(
            db.collection("users").document(userId).collection("clients")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Client::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<Client> { it.sortOrder }
                            .thenBy { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
                .flowOn(Dispatchers.Default)
        )
    }

    fun observeProjects(userId: String): Flow<List<ClientProject>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClientProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<ClientProject> { !it.defaultProject }
                            .thenBy { it.sortOrder }
                            .thenBy { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
                .flowOn(Dispatchers.Default)
        )
    }

    fun observeTasks(userId: String): Flow<List<ClientTask>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/tasks"
        emitAll(
            db.collection("users").document(userId).collection("tasks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClientTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<ClientTask> { it.completed }
                            .thenByDescending { priorityRank(it.priority) }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
                .flowOn(Dispatchers.Default)
        )
    }

    // --- AUTOMATIC LEGACY MIGRATION (EXISTING CLIENTS & TASKS -> DEFAULT PROJECT NAMED AFTER CLIENT) ---

    suspend fun migrateClientsAndTasksToDefaultProjects(
        userId: String,
        clients: List<Client>,
        projects: List<ClientProject>,
        tasks: List<ClientTask>
    ): Result<Int> {
        val path = "users/$userId/projects"
        return try {
            val uid = requireUserId(userId)
            val activeClients = clients.filter { !it.archived }
            if (activeClients.isEmpty()) return Result.success(0)

            val projectsByClient = projects.filter { !it.archived }.groupBy { it.clientId }
            val defaultProjectIdByClient = HashMap<String, String>(activeClients.size)
            val validProjectIdsByClient = HashMap<String, MutableSet<String>>(activeClients.size)

            val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

            for (client in activeClients) {
                val clientProjects = projectsByClient[client.id].orEmpty()
                val existingIds = clientProjects.mapTo(HashSet()) { it.id }
                validProjectIdsByClient[client.id] = existingIds

                if (clientProjects.isEmpty()) {
                    val defaultId = defaultProjectIdFor(client.id)
                    defaultProjectIdByClient[client.id] = defaultId
                    existingIds.add(defaultId)

                    val projectRef = db.collection("users").document(uid)
                        .collection("projects").document(defaultId)
                    val projectPayload = buildProjectPayload(
                        userId = uid,
                        clientId = client.id,
                        name = client.name.trim().ifBlank { "Default Project" }.take(80),
                        description = "",
                        colorHex = normalizeHexColor(client.colorHex),
                        sortOrder = 0L,
                        defaultProject = true
                    )
                    ops.add { batch -> batch.set(projectRef, projectPayload) }

                    if (client.portalEnabled && PortalCrypto.isValidPortalKey(client.portalKey)) {
                        val portalProjRef = db.collection("clientPortals")
                            .document(client.portalKey)
                            .collection("projects")
                            .document(defaultId)
                        val portalProjPayload = buildPortalProjectPayload(
                            portalKey = client.portalKey,
                            ownerId = uid,
                            clientId = client.id,
                            name = client.name.trim().ifBlank { "Default Project" }.take(80),
                            description = "",
                            colorHex = normalizeHexColor(client.colorHex),
                            sortOrder = 0L,
                            defaultProject = true
                        )
                        ops.add { batch -> batch.set(portalProjRef, portalProjPayload) }
                    }
                } else {
                    val def = clientProjects.firstOrNull { it.defaultProject } ?: clientProjects.first()
                    defaultProjectIdByClient[client.id] = def.id
                }
            }

            val clientById = activeClients.associateBy { it.id }
            var migratedTasks = 0
            for (task in tasks) {
                val targetClient = clientById[task.clientId] ?: continue
                val validProjects = validProjectIdsByClient[task.clientId].orEmpty()
                val needsMigration = task.projectId.isBlank() || task.projectId !in validProjects
                if (!needsMigration) continue

                val defaultId = defaultProjectIdByClient[task.clientId] ?: continue
                val taskRef = db.collection("users").document(uid)
                    .collection("tasks").document(task.id)
                ops.add { batch ->
                    batch.update(
                        taskRef,
                        mapOf(
                            "projectId" to defaultId,
                            "createdByClient" to task.createdByClient,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (targetClient.portalEnabled && PortalCrypto.isValidPortalKey(targetClient.portalKey)) {
                    val portalTaskRef = db.collection("clientPortals")
                        .document(targetClient.portalKey)
                        .collection("tasks")
                        .document(task.id)
                    val portalTaskPayload = buildPortalTaskPayload(
                        portalKey = targetClient.portalKey,
                        ownerId = uid,
                        clientId = task.clientId,
                        projectId = defaultId,
                        title = task.title.trim().take(140).ifBlank { "Task" },
                        details = task.details.trim().take(400),
                        priority = task.priorityEnum(),
                        completed = task.completed,
                        dueLabel = task.dueLabel.trim().take(40),
                        createdByClient = task.createdByClient
                    )
                    ops.add { batch -> batch.set(portalTaskRef, portalTaskPayload) }
                }
                migratedTasks++
            }

            if (ops.isEmpty()) return Result.success(0)

            ops.chunked(350).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { it(batch) }
                batch.commit().await()
            }
            Result.success(migratedTasks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- OWNER CLIENT CRUD (WITH DEFAULT PROJECT & PORTAL SYNC) ---

    suspend fun createClient(
        userId: String,
        name: String,
        company: String,
        notes: String,
        colorHex: String,
        sortOrder: Long,
        portalEnabled: Boolean = false,
        portalEmail: String = "",
        portalPassword: String = ""
    ): Result<String> {
        var docPath = "users/$userId/clients"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim()
            require(cleanName.length in 1..80) { "Client name must be 1 to 80 characters." }
            val cleanCompany = company.trim().take(80)
            val cleanNotes = notes.trim().take(300)
            val cleanHex = normalizeHexColor(colorHex)
            val cleanOrder = sortOrder.coerceIn(0L, 1_000_000L)

            val normalizedEmail: String
            val portalKey: String
            if (portalEnabled) {
                normalizedEmail = PortalCrypto.normalizeEmail(portalEmail)
                require(PortalCrypto.isValidEmail(normalizedEmail)) {
                    "Please enter a valid client portal email address."
                }
                require(portalPassword.length >= 6) {
                    "Portal password must be at least 6 characters."
                }
                portalKey = PortalCrypto.derivePortalKey(normalizedEmail, portalPassword)
            } else {
                normalizedEmail = ""
                portalKey = ""
            }

            val clientRef = db.collection("users").document(uid).collection("clients").document()
            docPath = clientRef.path
            val clientId = clientRef.id
            val defaultProjId = defaultProjectIdFor(clientId)
            val defaultProjRef = db.collection("users").document(uid)
                .collection("projects").document(defaultProjId)

            val batch = db.batch()
            batch.set(
                clientRef,
                mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "company" to cleanCompany,
                    "notes" to cleanNotes,
                    "colorHex" to cleanHex,
                    "sortOrder" to cleanOrder,
                    "archived" to false,
                    "portalEnabled" to portalEnabled,
                    "portalEmail" to normalizedEmail,
                    "portalKey" to portalKey,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            // Default project named after the client itself
            batch.set(
                defaultProjRef,
                buildProjectPayload(
                    userId = uid,
                    clientId = clientId,
                    name = cleanName,
                    description = "",
                    colorHex = cleanHex,
                    sortOrder = 0L,
                    defaultProject = true
                )
            )

            if (portalEnabled && portalKey.isNotBlank()) {
                val portalRef = db.collection("clientPortals").document(portalKey)
                batch.set(
                    portalRef,
                    buildClientPortalPayload(
                        ownerId = uid,
                        clientId = clientId,
                        clientName = cleanName,
                        clientCompany = cleanCompany,
                        clientColorHex = cleanHex,
                        portalEmail = normalizedEmail,
                        active = true
                    )
                )
                batch.set(
                    portalRef.collection("projects").document(defaultProjId),
                    buildPortalProjectPayload(
                        portalKey = portalKey,
                        ownerId = uid,
                        clientId = clientId,
                        name = cleanName,
                        description = "",
                        colorHex = cleanHex,
                        sortOrder = 0L,
                        defaultProject = true
                    )
                )
            }

            batch.commit().await()
            Result.success(clientId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClient(
        userId: String,
        existingClient: Client,
        name: String,
        company: String,
        notes: String,
        colorHex: String,
        portalEnabled: Boolean,
        portalEmail: String,
        newPortalPassword: String,
        clientProjects: List<ClientProject>,
        clientTasks: List<ClientTask>
    ): Result<Unit> {
        val clientId = existingClient.id
        val docPath = "users/$userId/clients/$clientId"
        return try {
            val uid = requireUserId(userId)
            require(clientId.isNotBlank()) { "Client ID must not be blank." }
            val cleanName = name.trim()
            require(cleanName.length in 1..80) { "Client name must be 1 to 80 characters." }
            val cleanCompany = company.trim().take(80)
            val cleanNotes = notes.trim().take(300)
            val cleanHex = normalizeHexColor(colorHex)

            val oldPortalKey = existingClient.portalKey
            val oldEmail = PortalCrypto.normalizeEmail(existingClient.portalEmail)

            val targetEmail: String
            val targetPortalKey: String
            var portalKeyChanged = false

            if (portalEnabled) {
                targetEmail = PortalCrypto.normalizeEmail(portalEmail)
                require(PortalCrypto.isValidEmail(targetEmail)) {
                    "Please enter a valid client portal email address."
                }
                val emailChanged = targetEmail != oldEmail
                val passwordProvided = newPortalPassword.isNotBlank()
                if (!existingClient.portalEnabled || oldPortalKey.isBlank() || emailChanged || passwordProvided) {
                    require(newPortalPassword.length >= 6) {
                        if (emailChanged && !passwordProvided) {
                            "Please enter a password (6+ characters) when changing the portal email."
                        } else {
                            "Portal password must be at least 6 characters."
                        }
                    }
                    targetPortalKey = PortalCrypto.derivePortalKey(targetEmail, newPortalPassword)
                    portalKeyChanged = targetPortalKey != oldPortalKey
                } else {
                    targetPortalKey = oldPortalKey
                }
            } else {
                targetEmail = ""
                targetPortalKey = ""
                portalKeyChanged = oldPortalKey.isNotBlank()
            }

            // 1. Update client document
            db.collection("users").document(uid).collection("clients").document(clientId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "company" to cleanCompany,
                        "notes" to cleanNotes,
                        "colorHex" to cleanHex,
                        "portalEnabled" to portalEnabled,
                        "portalEmail" to targetEmail,
                        "portalKey" to targetPortalKey,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            // 2. Purge old portal if disabled or key rotated
            if (portalKeyChanged && PortalCrypto.isValidPortalKey(oldPortalKey)) {
                deletePortalChannelInternal(oldPortalKey)
            }

            // 3. Create or update active portal mirror
            if (portalEnabled && PortalCrypto.isValidPortalKey(targetPortalKey)) {
                if (portalKeyChanged || !existingClient.portalEnabled) {
                    syncFullPortalMirrorInternal(
                        ownerId = uid,
                        portalKey = targetPortalKey,
                        clientId = clientId,
                        clientName = cleanName,
                        clientCompany = cleanCompany,
                        clientColorHex = cleanHex,
                        portalEmail = targetEmail,
                        projects = clientProjects.filter { !it.archived },
                        tasks = clientTasks
                    )
                } else {
                    val portalRef = db.collection("clientPortals").document(targetPortalKey)
                    val snap = portalRef.get().await()
                    if (snap.exists()) {
                        portalRef.update(
                            mapOf(
                                "clientName" to cleanName,
                                "clientCompany" to cleanCompany,
                                "clientColorHex" to cleanHex,
                                "portalEmail" to targetEmail,
                                "active" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } else {
                        syncFullPortalMirrorInternal(
                            ownerId = uid,
                            portalKey = targetPortalKey,
                            clientId = clientId,
                            clientName = cleanName,
                            clientCompany = cleanCompany,
                            clientColorHex = cleanHex,
                            portalEmail = targetEmail,
                            projects = clientProjects.filter { !it.archived },
                            tasks = clientTasks
                        )
                    }
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun reorderClients(
        userId: String,
        orderedClientSortOrders: List<Pair<String, Long>>
    ): Result<Unit> {
        val collectionPath = "users/$userId/clients"
        return try {
            val uid = requireUserId(userId)
            val validUpdates = orderedClientSortOrders.filter { it.first.isNotBlank() }
            if (validUpdates.isEmpty()) return Result.success(Unit)

            val clientsCollection = db.collection("users").document(uid).collection("clients")
            validUpdates.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (clientId, sortOrder) ->
                    val clampedOrder = sortOrder.coerceIn(0L, 1_000_000L)
                    batch.update(
                        clientsCollection.document(clientId),
                        mapOf(
                            "sortOrder" to clampedOrder,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, collectionPath)
            Result.failure(e)
        }
    }

    suspend fun deleteClientAndTasks(userId: String, client: Client): Result<Unit> {
        val clientId = client.id
        val docPath = "users/$userId/clients/$clientId"
        return try {
            val uid = requireUserId(userId)
            require(clientId.isNotBlank()) { "Client ID must not be blank." }

            if (PortalCrypto.isValidPortalKey(client.portalKey)) {
                deletePortalChannelInternal(client.portalKey)
            }

            val tasksSnapshot = db.collection("users").document(uid).collection("tasks")
                .whereEqualTo("clientId", clientId)
                .get()
                .await()
            val projectsSnapshot = db.collection("users").document(uid).collection("projects")
                .whereEqualTo("clientId", clientId)
                .get()
                .await()

            val refsToDelete = mutableListOf<DocumentReference>()
            tasksSnapshot.documents.forEach { refsToDelete.add(it.reference) }
            projectsSnapshot.documents.forEach { refsToDelete.add(it.reference) }
            refsToDelete.add(db.collection("users").document(uid).collection("clients").document(clientId))

            refsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- OWNER PROJECT CRUD (WITH PORTAL SYNC) ---

    suspend fun createProject(
        userId: String,
        client: Client,
        name: String,
        description: String,
        colorHex: String,
        sortOrder: Long
    ): Result<String> {
        var docPath = "users/$userId/projects"
        return try {
            val uid = requireUserId(userId)
            require(client.id.isNotBlank()) { "Client ID must not be blank." }
            val cleanName = name.trim()
            require(cleanName.length in 1..80) { "Project name must be 1 to 80 characters." }
            val cleanDesc = description.trim().take(300)
            val cleanHex = normalizeHexColor(colorHex)
            val cleanOrder = sortOrder.coerceIn(0L, 1_000_000L)

            val docRef = db.collection("users").document(uid).collection("projects").document()
            docPath = docRef.path

            val batch = db.batch()
            batch.set(
                docRef,
                buildProjectPayload(
                    userId = uid,
                    clientId = client.id,
                    name = cleanName,
                    description = cleanDesc,
                    colorHex = cleanHex,
                    sortOrder = cleanOrder,
                    defaultProject = false
                )
            )
            if (client.portalEnabled && PortalCrypto.isValidPortalKey(client.portalKey)) {
                val portalProjRef = db.collection("clientPortals")
                    .document(client.portalKey)
                    .collection("projects")
                    .document(docRef.id)
                batch.set(
                    portalProjRef,
                    buildPortalProjectPayload(
                        portalKey = client.portalKey,
                        ownerId = uid,
                        clientId = client.id,
                        name = cleanName,
                        description = cleanDesc,
                        colorHex = cleanHex,
                        sortOrder = cleanOrder,
                        defaultProject = false
                    )
                )
            }
            batch.commit().await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProject(
        userId: String,
        client: Client?,
        projectId: String,
        name: String,
        description: String,
        colorHex: String
    ): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId(userId)
            require(projectId.isNotBlank()) { "Project ID must not be blank." }
            val cleanName = name.trim()
            require(cleanName.length in 1..80) { "Project name must be 1 to 80 characters." }
            val cleanDesc = description.trim().take(300)
            val cleanHex = normalizeHexColor(colorHex)

            db.collection("users").document(uid).collection("projects").document(projectId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "description" to cleanDesc,
                        "colorHex" to cleanHex,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            if (client != null && client.portalEnabled && PortalCrypto.isValidPortalKey(client.portalKey)) {
                runCatching {
                    db.collection("clientPortals")
                        .document(client.portalKey)
                        .collection("projects")
                        .document(projectId)
                        .update(
                            mapOf(
                                "name" to cleanName,
                                "description" to cleanDesc,
                                "colorHex" to cleanHex,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProjectAndTasks(
        userId: String,
        client: Client?,
        projectId: String
    ): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId(userId)
            require(projectId.isNotBlank()) { "Project ID must not be blank." }

            val tasksSnapshot = db.collection("users").document(uid).collection("tasks")
                .whereEqualTo("projectId", projectId)
                .get()
                .await()

            val portalKey = client?.takeIf { it.portalEnabled && PortalCrypto.isValidPortalKey(it.portalKey) }?.portalKey
            val refsToDelete = mutableListOf<DocumentReference>()
            tasksSnapshot.documents.forEach { doc ->
                refsToDelete.add(doc.reference)
            }
            refsToDelete.add(db.collection("users").document(uid).collection("projects").document(projectId))

            refsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }

            if (portalKey != null) {
                runCatching {
                    val portalRef = db.collection("clientPortals").document(portalKey)
                    val portalTasksSnap = portalRef.collection("tasks")
                        .whereEqualTo("portalKey", portalKey)
                        .get()
                        .await()
                    val portalRefs = mutableListOf<DocumentReference>()
                    portalTasksSnap.documents
                        .filter { it.getString("projectId") == projectId }
                        .forEach { portalRefs.add(it.reference) }
                    val portalProjSnap = portalRef.collection("projects").document(projectId).get().await()
                    if (portalProjSnap.exists()) {
                        portalRefs.add(portalProjSnap.reference)
                    }
                    portalRefs.chunked(400).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.delete(it) }
                        batch.commit().await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- OWNER TASK CRUD (WITH PORTAL SYNC) ---

    suspend fun createTask(
        userId: String,
        clientId: String,
        projectId: String,
        title: String,
        details: String,
        priority: TaskPriority,
        dueLabel: String,
        portalKey: String = ""
    ): Result<String> {
        var docPath = "users/$userId/tasks"
        return try {
            val uid = requireUserId(userId)
            require(clientId.isNotBlank()) { "Please select a client first." }
            val resolvedProjectId = projectId.ifBlank { defaultProjectIdFor(clientId) }
            val cleanTitle = title.trim()
            require(cleanTitle.length in 1..140) { "Task title must be 1 to 140 characters." }
            val cleanDetails = details.trim().take(400)
            val cleanDue = dueLabel.trim().take(40)

            val docRef = db.collection("users").document(uid).collection("tasks").document()
            docPath = docRef.path

            val batch = db.batch()
            batch.set(
                docRef,
                mapOf(
                    "userId" to uid,
                    "clientId" to clientId,
                    "projectId" to resolvedProjectId,
                    "title" to cleanTitle,
                    "details" to cleanDetails,
                    "priority" to priority.raw,
                    "completed" to false,
                    "dueLabel" to cleanDue,
                    "createdByClient" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (PortalCrypto.isValidPortalKey(portalKey)) {
                val portalTaskRef = db.collection("clientPortals")
                    .document(portalKey)
                    .collection("tasks")
                    .document(docRef.id)
                batch.set(
                    portalTaskRef,
                    buildPortalTaskPayload(
                        portalKey = portalKey,
                        ownerId = uid,
                        clientId = clientId,
                        projectId = resolvedProjectId,
                        title = cleanTitle,
                        details = cleanDetails,
                        priority = priority,
                        completed = false,
                        dueLabel = cleanDue,
                        createdByClient = false
                    )
                )
            }
            batch.commit().await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(
        userId: String,
        taskId: String,
        completed: Boolean,
        portalKey: String = ""
    ): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            db.collection("users").document(uid).collection("tasks").document(taskId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            if (PortalCrypto.isValidPortalKey(portalKey)) {
                runCatching {
                    db.collection("clientPortals")
                        .document(portalKey)
                        .collection("tasks")
                        .document(taskId)
                        .update(
                            mapOf(
                                "completed" to completed,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        userId: String,
        taskId: String,
        projectId: String,
        title: String,
        details: String,
        priority: TaskPriority,
        dueLabel: String,
        completed: Boolean,
        portalKey: String = ""
    ): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            val cleanTitle = title.trim()
            require(cleanTitle.length in 1..140) { "Task title must be 1 to 140 characters." }
            val cleanDetails = details.trim().take(400)
            val cleanDue = dueLabel.trim().take(40)

            val updatePayload = buildMap<String, Any> {
                put("title", cleanTitle)
                put("details", cleanDetails)
                put("priority", priority.raw)
                put("dueLabel", cleanDue)
                put("completed", completed)
                if (projectId.isNotBlank()) {
                    put("projectId", projectId)
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }

            db.collection("users").document(uid).collection("tasks").document(taskId)
                .update(updatePayload)
                .await()

            if (PortalCrypto.isValidPortalKey(portalKey)) {
                runCatching {
                    db.collection("clientPortals")
                        .document(portalKey)
                        .collection("tasks")
                        .document(taskId)
                        .update(updatePayload)
                        .await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(
        userId: String,
        taskId: String,
        portalKey: String = ""
    ): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            db.collection("users").document(uid).collection("tasks").document(taskId)
                .delete()
                .await()
            if (PortalCrypto.isValidPortalKey(portalKey)) {
                runCatching {
                    db.collection("clientPortals").document(portalKey)
                        .collection("tasks").document(taskId)
                        .delete()
                        .await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- OWNER INGESTION OF CLIENT-SUBMITTED TASKS ---

    suspend fun ingestClientSubmittedTasks(
        userId: String,
        client: Client,
        portalTasks: List<PortalTask>,
        existingOwnerTaskIds: Set<String>,
        fallbackProjectId: String
    ): Result<Int> {
        val path = "users/$userId/tasks"
        return try {
            val uid = requireUserId(userId)
            val newTasks = portalTasks.filter {
                it.createdByClient &&
                    it.clientId == client.id &&
                    it.id.isNotBlank() &&
                    it.id !in existingOwnerTaskIds
            }
            if (newTasks.isEmpty()) return Result.success(0)

            var ingested = 0
            newTasks.chunked(350).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { pt ->
                    val targetProjectId = pt.projectId.ifBlank { fallbackProjectId }
                    if (targetProjectId.isNotBlank()) {
                        val ref = db.collection("users").document(uid)
                            .collection("tasks").document(pt.id)
                        batch.set(
                            ref,
                            mapOf(
                                "userId" to uid,
                                "clientId" to client.id,
                                "projectId" to targetProjectId,
                                "title" to pt.title.trim().take(140).ifBlank { "Client Task" },
                                "details" to pt.details.trim().take(400),
                                "priority" to pt.priorityEnum().raw,
                                "completed" to pt.completed,
                                "dueLabel" to pt.dueLabel.trim().take(40),
                                "createdByClient" to true,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        ingested++
                    }
                }
                if (ingested > 0) {
                    batch.commit().await()
                }
            }
            Result.success(ingested)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Client task ingestion skipped/retried: ${e.message}")
            Result.failure(e)
        }
    }

    // --- CLIENT PORTAL AUTH & REAL-TIME STREAMS (NO GOOGLE AUTH NEEDED) ---

    suspend fun authenticateClientPortal(
        email: String,
        password: String
    ): Result<ClientPortalSession> {
        val normalizedEmail = PortalCrypto.normalizeEmail(email)
        if (!PortalCrypto.isValidEmail(normalizedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Invalid email or password."))
        }
        return try {
            val portalKey = PortalCrypto.derivePortalKey(normalizedEmail, password)
            val snap = db.collection("clientPortals").document(portalKey).get().await()
            val profile = snap.toObject(
                ClientPortalProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            if (profile == null || !profile.active || !profile.portalEmail.equals(normalizedEmail, ignoreCase = true)) {
                Result.failure(IllegalArgumentException("Invalid email or password, or portal access is disabled."))
            } else {
                Result.success(
                    ClientPortalSession(
                        portalKey = portalKey,
                        ownerId = profile.ownerId,
                        clientId = profile.clientId,
                        clientName = profile.clientName,
                        clientCompany = profile.clientCompany,
                        clientColorHex = profile.clientColorHex,
                        portalEmail = profile.portalEmail
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Result.failure(IllegalArgumentException("Invalid email or password."))
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePortalProfile(portalKey: String): Flow<ClientPortalProfile?> = flow {
        require(PortalCrypto.isValidPortalKey(portalKey)) { "Invalid portal session key." }
        emitAll(
            db.collection("clientPortals").document(portalKey)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(
                        ClientPortalProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(null)
                    } else {
                        throw error
                    }
                }
                .flowOn(Dispatchers.Default)
        )
    }

    fun observePortalProjects(portalKey: String): Flow<List<PortalProject>> = flow {
        require(PortalCrypto.isValidPortalKey(portalKey)) { "Invalid portal session key." }
        emitAll(
            db.collection("clientPortals").document(portalKey).collection("projects")
                .whereEqualTo("portalKey", portalKey)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PortalProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<PortalProject> { !it.defaultProject }
                            .thenBy { it.sortOrder }
                            .thenBy { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    throw error
                }
                .flowOn(Dispatchers.Default)
        )
    }

    fun observePortalTasks(portalKey: String): Flow<List<PortalTask>> = flow {
        require(PortalCrypto.isValidPortalKey(portalKey)) { "Invalid portal session key." }
        emitAll(
            db.collection("clientPortals").document(portalKey).collection("tasks")
                .whereEqualTo("portalKey", portalKey)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PortalTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<PortalTask> { it.completed }
                            .thenByDescending { priorityRank(it.priority) }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    throw error
                }
                .flowOn(Dispatchers.Default)
        )
    }

    suspend fun submitPortalTask(
        session: ClientPortalSession,
        projectId: String,
        title: String,
        details: String,
        priority: TaskPriority,
        dueLabel: String
    ): Result<String> {
        var docPath = "clientPortals/${session.portalKey}/tasks"
        return try {
            require(PortalCrypto.isValidPortalKey(session.portalKey)) { "Invalid portal session." }
            require(projectId.isNotBlank()) { "Please pick a project." }
            val cleanTitle = title.trim()
            require(cleanTitle.length in 1..140) { "Task title must be 1 to 140 characters." }
            val cleanDetails = details.trim().take(400)
            val cleanDue = dueLabel.trim().take(40)

            val docRef = db.collection("clientPortals")
                .document(session.portalKey)
                .collection("tasks")
                .document()
            docPath = docRef.path

            docRef.set(
                buildPortalTaskPayload(
                    portalKey = session.portalKey,
                    ownerId = session.ownerId,
                    clientId = session.clientId,
                    projectId = projectId,
                    title = cleanTitle,
                    details = cleanDetails,
                    priority = priority,
                    completed = false,
                    dueLabel = cleanDue,
                    createdByClient = true
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    // --- INTERNAL HELPERS ---

    private suspend fun syncFullPortalMirrorInternal(
        ownerId: String,
        portalKey: String,
        clientId: String,
        clientName: String,
        clientCompany: String,
        clientColorHex: String,
        portalEmail: String,
        projects: List<ClientProject>,
        tasks: List<ClientTask>
    ) {
        val portalRef = db.collection("clientPortals").document(portalKey)
        val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

        ops.add { batch ->
            batch.set(
                portalRef,
                buildClientPortalPayload(
                    ownerId = ownerId,
                    clientId = clientId,
                    clientName = clientName,
                    clientCompany = clientCompany,
                    clientColorHex = clientColorHex,
                    portalEmail = portalEmail,
                    active = true
                )
            )
        }

        val effectiveProjects = if (projects.isEmpty()) {
            listOf(
                ClientProject(
                    id = defaultProjectIdFor(clientId),
                    userId = ownerId,
                    clientId = clientId,
                    name = clientName,
                    description = "",
                    colorHex = clientColorHex,
                    sortOrder = 0L,
                    defaultProject = true
                )
            )
        } else {
            projects
        }
        val fallbackProjId = effectiveProjects.firstOrNull { it.defaultProject }?.id
            ?: effectiveProjects.first().id

        effectiveProjects.forEach { proj ->
            ops.add { batch ->
                batch.set(
                    portalRef.collection("projects").document(proj.id),
                    buildPortalProjectPayload(
                        portalKey = portalKey,
                        ownerId = ownerId,
                        clientId = clientId,
                        name = proj.name.trim().ifBlank { clientName }.take(80),
                        description = proj.description.trim().take(300),
                        colorHex = normalizeHexColor(proj.colorHex),
                        sortOrder = proj.sortOrder.coerceIn(0L, 1_000_000L),
                        defaultProject = proj.defaultProject
                    )
                )
            }
        }

        tasks.forEach { task ->
            val resolvedProjId = task.projectId.ifBlank { fallbackProjId }
            ops.add { batch ->
                batch.set(
                    portalRef.collection("tasks").document(task.id),
                    buildPortalTaskPayload(
                        portalKey = portalKey,
                        ownerId = ownerId,
                        clientId = clientId,
                        projectId = resolvedProjId,
                        title = task.title.trim().ifBlank { "Task" }.take(140),
                        details = task.details.trim().take(400),
                        priority = task.priorityEnum(),
                        completed = task.completed,
                        dueLabel = task.dueLabel.trim().take(40),
                        createdByClient = task.createdByClient
                    )
                )
            }
        }

        ops.chunked(350).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }

    private suspend fun deletePortalChannelInternal(portalKey: String) {
        runCatching {
            val portalRef = db.collection("clientPortals").document(portalKey)
            val tasksSnap = portalRef.collection("tasks")
                .whereEqualTo("portalKey", portalKey)
                .get()
                .await()
            val projectsSnap = portalRef.collection("projects")
                .whereEqualTo("portalKey", portalKey)
                .get()
                .await()
            val refs = mutableListOf<DocumentReference>()
            tasksSnap.documents.forEach { refs.add(it.reference) }
            projectsSnap.documents.forEach { refs.add(it.reference) }
            refs.add(portalRef)
            refs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
        }
    }

    private fun handleFlowError(
        error: Throwable,
        operation: OperationType,
        path: String,
        userId: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, operation, path)
        }
        throw error
    }

    private fun buildProjectPayload(
        userId: String,
        clientId: String,
        name: String,
        description: String,
        colorHex: String,
        sortOrder: Long,
        defaultProject: Boolean
    ): Map<String, Any> = mapOf(
        "userId" to userId,
        "clientId" to clientId,
        "name" to name,
        "description" to description,
        "colorHex" to colorHex,
        "sortOrder" to sortOrder,
        "defaultProject" to defaultProject,
        "archived" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun buildClientPortalPayload(
        ownerId: String,
        clientId: String,
        clientName: String,
        clientCompany: String,
        clientColorHex: String,
        portalEmail: String,
        active: Boolean
    ): Map<String, Any> = mapOf(
        "ownerId" to ownerId,
        "clientId" to clientId,
        "clientName" to clientName,
        "clientCompany" to clientCompany,
        "clientColorHex" to clientColorHex,
        "portalEmail" to portalEmail,
        "active" to active,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun buildPortalProjectPayload(
        portalKey: String,
        ownerId: String,
        clientId: String,
        name: String,
        description: String,
        colorHex: String,
        sortOrder: Long,
        defaultProject: Boolean
    ): Map<String, Any> = mapOf(
        "portalKey" to portalKey,
        "ownerId" to ownerId,
        "clientId" to clientId,
        "name" to name,
        "description" to description,
        "colorHex" to colorHex,
        "sortOrder" to sortOrder,
        "defaultProject" to defaultProject,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun buildPortalTaskPayload(
        portalKey: String,
        ownerId: String,
        clientId: String,
        projectId: String,
        title: String,
        details: String,
        priority: TaskPriority,
        completed: Boolean,
        dueLabel: String,
        createdByClient: Boolean
    ): Map<String, Any> = mapOf(
        "portalKey" to portalKey,
        "ownerId" to ownerId,
        "clientId" to clientId,
        "projectId" to projectId,
        "title" to title,
        "details" to details,
        "priority" to priority.raw,
        "completed" to completed,
        "dueLabel" to dueLabel,
        "createdByClient" to createdByClient,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun normalizeHexColor(hex: String): String {
        val trimmed = hex.trim()
        return if (trimmed.matches(Regex("^#[0-9A-Fa-f]{6}$"))) trimmed else "#2555E6"
    }

    private fun priorityRank(raw: String): Int = when (TaskPriority.fromRaw(raw)) {
        TaskPriority.HIGH -> 3
        TaskPriority.NORMAL -> 2
        TaskPriority.LOW -> 1
    }

    companion object {
        private const val TAG = "ClientDeskRepo"

        fun defaultProjectIdFor(clientId: String): String =
            "default_${clientId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(110)}"
    }
}
