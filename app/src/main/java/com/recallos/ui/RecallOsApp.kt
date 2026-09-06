package com.recallos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun RecallOsApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RecallInk,
            onPrimary = RecallYellow,
            secondary = RecallYellow,
            onSecondary = RecallInk,
            background = Color.White,
            surface = Color.White,
            onBackground = RecallInk,
            onSurface = RecallInk,
            secondaryContainer = RecallYellow,
            onSecondaryContainer = RecallInk,
            tertiaryContainer = RecallYellowSoft,
            onTertiaryContainer = RecallInk,
        ),
        typography = RecallTypography,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            var selectedTab by remember { mutableStateOf(RecallOsDestination.Capture) }

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        RecallOsDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = selectedTab == destination,
                                onClick = { selectedTab = destination },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    when (selectedTab) {
                        RecallOsDestination.Capture -> CaptureScreen()
                        RecallOsDestination.Search -> SearchScreen()
                    }
                }
            }
        }
    }
}

private enum class RecallOsDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Capture("Capture", Icons.Outlined.AddAPhoto),
    Search("Search", Icons.Outlined.Search),
}
