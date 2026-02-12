package com.plankton.one102.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plankton.one102.ui.components.GlassBackground

@Composable
fun PlaceholderScreen(title: String, padding: PaddingValues) {
    GlassBackground {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        )
    }
}
