package com.example.ui

import androidx.compose.runtime.Composable
import com.example.model.GeneratedFile

@Composable
fun FilePreviewDialog(
    file: GeneratedFile,
    onDismiss: () -> Unit
) {
    FullScreenFilePreview(file = file, onDismiss = onDismiss)
}
