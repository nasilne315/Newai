package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.GeneratedFile
import com.example.ui.theme.DarkBg
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsState()
    val chatSessions by viewModel.chatSessions.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val rules by viewModel.rules.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val isImeVisible = WindowInsets.isImeVisible

    // Synchronize drawer state
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }
    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen && uiState.isDrawerOpen) {
            viewModel.setDrawerOpen(false)
        }
    }

    // Scroll to bottom when new messages arrive or loading begins
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size)
        }
    }

    // Auto-scroll to latest message when keyboard opens
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size)
        }
    }

    // Error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearNotice()
        }
    }

    // Failover and info notices
    LaunchedEffect(uiState.infoNotice) {
        uiState.infoNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    var previewFile by remember { mutableStateOf<GeneratedFile?>(null) }
    var isCompanionModeOpen by remember { mutableStateOf(false) }

    // Handle back button
    BackHandler(enabled = isCompanionModeOpen || previewFile != null || drawerState.isOpen || uiState.isApiKeyDialogOpen || uiState.isMemoryDialogOpen) {
        if (isCompanionModeOpen) {
            isCompanionModeOpen = false
        } else if (previewFile != null) {
            previewFile = null
        } else if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
            viewModel.setDrawerOpen(false)
        } else if (uiState.isApiKeyDialogOpen) {
            viewModel.setApiKeyDialogOpen(false)
        } else if (uiState.isMemoryDialogOpen) {
            viewModel.setMemoryDialogOpen(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ChatDrawerContent(
                    chatSessions = chatSessions,
                    currentSessionId = uiState.currentSessionId,
                    onOpenMemoryDialog = { viewModel.setMemoryDialogOpen(true) },
                    onSelectSession = { viewModel.selectSession(it) },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    onNewChat = { viewModel.startNewChat() },
                    onClearChat = { viewModel.clearHistory() },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                        viewModel.setDrawerOpen(false)
                    }
                )
            },
            modifier = modifier.testTag("chat_navigation_drawer")
        ) {
            Scaffold(
                topBar = {
                    ChatHeader(
                        onMenuClick = {
                            coroutineScope.launch { drawerState.open() }
                            viewModel.setDrawerOpen(true)
                        },
                        onNewChatClick = {
                            viewModel.startNewChat()
                        },
                        onMoreClick = {
                            viewModel.setMoreMenuOpen(!uiState.isMoreMenuOpen)
                        },
                        isMoreMenuOpen = uiState.isMoreMenuOpen,
                        onDismissMoreMenu = {
                            viewModel.setMoreMenuOpen(false)
                        },
                        onClearChatClick = {
                            viewModel.clearHistory()
                        },
                        onOpenApiKeysDialog = {
                            viewModel.setApiKeyDialogOpen(true)
                        },
                        onOpenCompanion = {
                            isCompanionModeOpen = true
                        },
                        isThinking = uiState.isLoading,
                        isSpeaking = uiState.isSpeaking
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = DarkBg,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(DarkBg)
                ) {
                    ChatMessageList(
                        messages = uiState.messages,
                        isLoading = uiState.isLoading,
                        listState = listState,
                        isSpeaking = uiState.isSpeaking,
                        speakingMessageId = uiState.speakingMessageId,
                        onToggleSpeak = { text, id ->
                            viewModel.toggleSpeakMessage(text, id)
                        },
                        onSuggestionClick = { prompt ->
                            viewModel.sendMessage(prompt)
                        },
                        onOpenFile = { file ->
                            previewFile = file
                        },
                        bottomContentPadding = 92.dp,
                        modifier = Modifier.fillMaxSize()
                    )

                    ChatInputBar(
                        inputText = uiState.inputText,
                        onInputTextChanged = { viewModel.onInputTextChanged(it) },
                        attachedFile = uiState.attachedFile,
                        isRecordingVoice = uiState.isRecordingVoice,
                        isLoading = uiState.isLoading,
                        onAttachFile = { viewModel.attachFile(it) },
                        onRemoveAttachedFile = { viewModel.removeAttachedFile() },
                        onToggleVoiceRecording = { viewModel.toggleVoiceRecording() },
                        onSendMessage = { viewModel.sendMessage() },
                        isWebSearchEnabled = uiState.isWebSearchEnabled,
                        onToggleWebSearch = { viewModel.toggleWebSearch() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    )
                }
            }
        }

        if (isCompanionModeOpen) {
            com.example.ui.avatar.CompanionScreen(
                viewModel = viewModel,
                onDismiss = { isCompanionModeOpen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        val activePreviewFile = previewFile
        if (activePreviewFile != null) {
            FullScreenFilePreview(
                file = activePreviewFile,
                onDismiss = { previewFile = null },
                modifier = Modifier.fillMaxSize()
            )
        }

        // API Key Dialog
        if (uiState.isApiKeyDialogOpen) {
            ApiKeyDialog(
                status = apiKeyStatus,
                onSaveKeys = { k1, k2, k3, model ->
                    viewModel.saveApiKeys(k1, k2, k3, model)
                },
                onDismiss = {
                    viewModel.setApiKeyDialogOpen(false)
                }
            )
        }

        // Personal Memory & Rules Management Dialog
        if (uiState.isMemoryDialogOpen) {
            MemoryDialog(
                memories = memories,
                rules = rules,
                onAddMemory = { content ->
                    viewModel.addMemory(content)
                },
                onRemoveMemory = { id ->
                    viewModel.removeMemory(id)
                },
                onClearAllMemories = {
                    viewModel.clearMemories()
                },
                onAddRule = { content ->
                    viewModel.addRule(content)
                },
                onRemoveRule = { id ->
                    viewModel.removeRule(id)
                },
                onClearAllRules = {
                    viewModel.clearRules()
                },
                onDismiss = {
                    viewModel.setMemoryDialogOpen(false)
                }
            )
        }
    }
}
