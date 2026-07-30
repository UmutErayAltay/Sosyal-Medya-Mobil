package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.GroupMemberDto
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.GroupManageEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.GroupManageViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.GroupManageViewModelFactory

/**
 * "Grubu Yönet" ekranı — üye listesi (admin rozeti + admin-toggle/çıkar
 * aksiyonları, SADECE isSelfAdmin iken ve kendi satırın HARİÇ), grup adını
 * (SADECE admin) inline düzenleme, "+ Üye Ekle" alt-akışı (GroupCreateScreen'in
 * AYNI SelectableUserRow'unu reuse eder — arama+çoklu-seçim, sonra "Ekle") ve
 * HERKES için "Gruptan Ayrıl" (SettingsScreen'in deaktivasyon AlertDialog
 * DESENİYLE, error renkli confirm/dismiss).
 *
 * Üye ekleme modu ayrı bir NAV ROUTE DEĞİL — [GroupManageViewModel.showAddMembers]
 * true olunca AYNI ekranın TopAppBar'ı ve gövdesi "Üye Ekle" arama moduna geçer,
 * "geri" ikonu bu modda ekranı KAPATMAZ, sadece arama modundan çıkar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onLeftGroup: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: GroupManageViewModel = viewModel(
        factory = GroupManageViewModelFactory(conversationId),
    ),
) {
    val groupName by viewModel.groupName.collectAsState()
    val members by viewModel.members.collectAsState()
    val myUserId by viewModel.myUserId.collectAsState()
    val isSelfAdmin by viewModel.isSelfAdmin.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val renaming by viewModel.renaming.collectAsState()
    val leaving by viewModel.leaving.collectAsState()
    val memberActionInProgress by viewModel.memberActionInProgress.collectAsState()

    val showAddMembers by viewModel.showAddMembers.collectAsState()
    val addQuery by viewModel.addQuery.collectAsState()
    val addResults by viewModel.addResults.collectAsState()
    val addSelected by viewModel.addSelected.collectAsState()
    val addSearching by viewModel.addSearching.collectAsState()
    val addingMembers by viewModel.addingMembers.collectAsState()

    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember(groupName) { mutableStateOf(groupName) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupManageEvent.LeftGroup -> {
                    showLeaveDialog = false
                    onLeftGroup()
                }
                is GroupManageEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            if (showAddMembers) {
                TopAppBar(
                    title = { Text("Üye Ekle") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeAddMembers() }, enabled = !addingMembers) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    },
                    actions = {
                        if (addingMembers) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(
                                onClick = viewModel::confirmAddMembers,
                                enabled = addSelected.isNotEmpty(),
                            ) { Text("Ekle") }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(groupName.ifBlank { "Grubu Yönet" }) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (showAddMembers) {
            AddMembersContent(
                padding = padding,
                query = addQuery,
                onQueryChange = viewModel::onAddQueryChange,
                results = addResults,
                selected = addSelected,
                searching = addSearching,
                adding = addingMembers,
                onToggleSelected = viewModel::toggleAddSelected,
            )
            return@Scaffold
        }

        when {
            loading && members.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && members.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editingName) {
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !renaming,
                        )
                        if (renaming) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = {
                                viewModel.renameGroup(nameDraft)
                                editingName = false
                            }) {
                                Icon(Icons.Filled.Check, contentDescription = "Kaydet")
                            }
                            IconButton(onClick = {
                                nameDraft = groupName
                                editingName = false
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Vazgeç")
                            }
                        }
                    } else {
                        Text(
                            text = groupName.ifBlank { "Grup" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelfAdmin) {
                            IconButton(onClick = {
                                nameDraft = groupName
                                editingName = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Grup adını düzenle")
                            }
                        }
                    }
                }

                HorizontalDivider()

                if (actionError != null) {
                    Text(
                        text = actionError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Text(
                    text = "Üyeler (${members.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(members, key = { it.id }) { member ->
                        val isSelf = member.id == myUserId
                        MemberRow(
                            member = member,
                            isSelf = isSelf,
                            canManage = isSelfAdmin && !isSelf,
                            actionInProgress = memberActionInProgress == member.id,
                            onToggleAdmin = { viewModel.toggleAdmin(member.id) },
                            onRemove = { viewModel.removeMember(member.id) },
                        )
                    }
                }

                if (isSelfAdmin) {
                    OutlinedButton(
                        onClick = { viewModel.openAddMembers() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = null)
                        Text(text = "Üye Ekle", modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Button(
                    onClick = { showLeaveDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Text("Gruptan Ayrıl")
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!leaving) showLeaveDialog = false },
            title = { Text("Gruptan ayrıl") },
            text = {
                Column {
                    Text(
                        "Bu gruptan ayrılacaksın ve tekrar eklenmediğin sürece mesajlarını " +
                            "göremeyeceksin. Emin misin?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (leaving) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::leaveGroup, enabled = !leaving) {
                    Text("Ayrıl", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }, enabled = !leaving) {
                    Text("Vazgeç")
                }
            },
        )
    }
}

@Composable
private fun MemberRow(
    member: GroupMemberDto,
    isSelf: Boolean,
    canManage: Boolean,
    actionInProgress: Boolean,
    onToggleAdmin: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!member.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(imageVector = Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = (member.username ?: "bilinmeyen") + if (isSelf) " (sen)" else "",
                style = MaterialTheme.typography.titleSmall,
            )
            if (member.isAdmin) {
                Text(
                    text = "Admin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (actionInProgress) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (canManage) {
            IconButton(onClick = onToggleAdmin) {
                Icon(
                    Icons.Filled.AdminPanelSettings,
                    contentDescription = if (member.isAdmin) "Adminlikten al" else "Admin yap",
                    tint = if (member.isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.PersonRemove,
                    contentDescription = "Gruptan çıkar",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddMembersContent(
    padding: PaddingValues,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<UserSearchDto>,
    selected: List<UserSearchDto>,
    searching: Boolean,
    adding: Boolean,
    onToggleSelected: (UserSearchDto) -> Unit,
) {
    val selectedIds = selected.mapTo(HashSet()) { it.id }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            placeholder = { Text("Kullanıcı adı ara") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            enabled = !adding,
        )
        if (selected.isNotEmpty()) {
            Text(
                text = "${selected.size} kişi seçildi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        when {
            searching && results.isEmpty() -> CenteredBox { CircularProgressIndicator() }
            query.length < 2 -> CenteredBox { Text("Aramak için en az 2 karakter yaz") }
            results.isEmpty() -> CenteredBox { Text("Kullanıcı bulunamadı") }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(results, key = { it.id }) { user ->
                    SelectableUserRow(
                        avatarUrl = user.avatarUrl,
                        username = user.username,
                        fullName = user.fullName,
                        selected = user.id in selectedIds,
                        enabled = !adding,
                        onClick = { onToggleSelected(user) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun CenteredMessage(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
