package dev.dettmer.simplenotes.ui.settings.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader

/**
 * About app information screen
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    val githubRepoUrl = "https://github.com/inventory69/simple-notes-sync"
    val githubProfileUrl = "https://github.com/inventory69"
    val licenseUrl = "https://github.com/inventory69/simple-notes-sync/blob/main/LICENSE"
    val changelogUrl = "https://github.com/inventory69/simple-notes-sync/blob/main/CHANGELOG.md"  // v1.8.0
    
    SettingsScaffold(
        title = stringResource(R.string.about_settings_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // App Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // v1.5.0: App icon foreground loaded directly for better quality
                    val context = LocalContext.current
                    val appIcon = remember {
                        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_foreground)
                        drawable?.let {
                            // Use fixed size for consistent quality (256x256)
                            val size = 256
                            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            it.setBounds(0, 0, size, size)
                            it.draw(canvas)
                            bitmap.asImageBitmap()
                        }
                    }
                    
                    appIcon?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(R.string.about_app_name),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.about_app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            SettingsSectionHeader(text = stringResource(R.string.about_links_section))
            
            // GitHub Repository
            AboutLinkItem(
                icon = Icons.Default.Code,
                title = stringResource(R.string.about_github_title),
                subtitle = stringResource(R.string.about_github_subtitle),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubRepoUrl))
                    context.startActivity(intent)
                }
            )
            
            // Developer
            AboutLinkItem(
                icon = Icons.Default.Person,
                title = stringResource(R.string.about_developer_title),
                subtitle = stringResource(R.string.about_developer_subtitle),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubProfileUrl))
                    context.startActivity(intent)
                }
            )
            
            // License
            AboutLinkItem(
                icon = Icons.Default.Policy,
                title = stringResource(R.string.about_license_title),
                subtitle = stringResource(R.string.about_license_subtitle),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(licenseUrl))
                    context.startActivity(intent)
                }
            )
            
            // v1.8.0: Changelog
            AboutLinkItem(
                icon = Icons.Default.History,
                title = stringResource(R.string.about_changelog_title),
                subtitle = stringResource(R.string.about_changelog_subtitle),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(changelogUrl))
                    context.startActivity(intent)
                }
            )
            
            SettingsDivider()
            
            // Data Privacy Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_privacy_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_privacy_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Clickable link item for About section
 */
@Composable
private fun AboutLinkItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
