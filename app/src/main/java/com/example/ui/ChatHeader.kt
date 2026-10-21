package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ChatHeader(
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onMoreClick: () -> Unit,
    isMoreMenuOpen: Boolean,
    onDismissMoreMenu: () -> Unit,
    onClearChatClick: () -> Unit,
    onOpenApiKeysDialog: () -> Unit,
    onOpenCompanion: () -> Unit = {},
    isThinking: Boolean = false,
    isSpeaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: single round menu button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DarkSurface)
                .border(1.dp, DarkBorder, CircleShape)
                .clickable(onClick = onMenuClick)
                .testTag("menu_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = "Menu",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Center: Animated living mini eyes avatar
        com.example.ui.avatar.MiniHeaderEyes(
            onClick = onOpenCompanion,
            isThinking = isThinking,
            isSpeaking = isSpeaking,
            modifier = Modifier.testTag("header_avatar_button")
        )

        // Right: one rounded bubble containing two buttons side by side (add + more)
        Box {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("header_actions_bubble")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = onNewChatClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New chat",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(DarkBorder)
                    )

                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("three_dots_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Dropdown menu from the three dots button
            DropdownMenu(
                expanded = isMoreMenuOpen,
                onDismissRequest = onDismissMoreMenu,
                modifier = Modifier
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .testTag("more_dropdown_menu")
            ) {
                DropdownMenuItem(
                    text = { Text("API Anahtarları (3x Pool)", color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    onClick = {
                        onDismissMoreMenu()
                        onOpenApiKeysDialog()
                    },
                    modifier = Modifier.testTag("menu_item_api_keys")
                )
                DropdownMenuItem(
                    text = { Text("Sohbeti Temizle", color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    onClick = {
                        onDismissMoreMenu()
                        onClearChatClick()
                    },
                    modifier = Modifier.testTag("menu_item_clear_chat")
                )
            }
        }
    }
}
