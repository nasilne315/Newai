package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ApiKeyStatus
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SendButtonDarkBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Bolt

@Composable
fun ApiKeyDialog(
    status: ApiKeyStatus,
    onSaveKeys: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var key1 by remember { mutableStateOf(status.key1) }
    var key2 by remember { mutableStateOf(status.key2) }
    var key3 by remember { mutableStateOf(status.key3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .border(BorderStroke(1.dp, DarkBorder), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VpnKey,
                        contentDescription = null,
                        tint = SendButtonDarkBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "API Anahtarları Havuzu",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Flash Lite Latest • Ultra Hızlı Otomatik Havuz",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF60A5FA)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "3 API anahtarı havuzu (429/503 durumunda otomatik devir).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = 16.sp
                )

                // Key 1 Field
                ApiKeyInputField(
                    label = "Anahtar 1 (Birincil)",
                    value = key1,
                    onValueChange = { key1 = it },
                    isActive = status.activeIndex == 0 && key1.isNotBlank(),
                    tag = "api_key_1_input"
                )

                // Key 2 Field
                ApiKeyInputField(
                    label = "Anahtar 2 (Yedek)",
                    value = key2,
                    onValueChange = { key2 = it },
                    isActive = status.activeIndex == 1 && key2.isNotBlank(),
                    tag = "api_key_2_input"
                )

                // Key 3 Field
                ApiKeyInputField(
                    label = "Anahtar 3 (Yedek)",
                    value = key3,
                    onValueChange = { key3 = it },
                    isActive = status.activeIndex == 2 && key3.isNotBlank(),
                    tag = "api_key_3_input"
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Locked single model badge (Flash Lite Latest)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkBg,
                    border = BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E3A8A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Model: Gemini Flash Lite Latest",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Kilitli ve optimize tek model • Ultra hızlı yanıt",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveKeys(key1, key2, key3, "gemini-2.0-flash-lite")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SendButtonDarkBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_api_keys_button")
            ) {
                Text("Kaydet", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("Kapat")
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_key_dialog")
    )
}

@Composable
private fun ApiKeyInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isActive: Boolean,
    tag: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = TextSecondary
            )
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Aktif",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("AIzaSy...", color = TextMuted, fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = SendButtonDarkBlue,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
        )
    }
}
