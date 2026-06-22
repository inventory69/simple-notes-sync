package dev.dettmer.simplenotes.ui.settings.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.google.gson.Gson
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Contributor
import dev.dettmer.simplenotes.models.ContributorsFile
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.theme.Dimensions

private val ROLE_ORDER = listOf("code", "testing", "translation")

@Composable
fun ContributorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // ponytail: sync load — contributors.json is a tiny local asset (~200 B), no perceptible block
    val grouped = remember {
        val json = context.assets.open("contributors.json").bufferedReader().readText()
        val data = Gson().fromJson(json, ContributorsFile::class.java)
        data.contributors
            .groupBy { it.role }
            .let { g -> ROLE_ORDER.filter { g.containsKey(it) }.associateWith { g[it]!! } }
    }

    SettingsScaffold(title = stringResource(R.string.contributors_title), onBack = onBack) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            grouped.forEach { (role, contributors) ->
                val headerRes = when (role) {
                    "code" -> R.string.contributors_role_code
                    "translation" -> R.string.contributors_role_translation
                    "testing" -> R.string.contributors_role_testing
                    else -> return@forEach
                }
                item { SettingsSectionHeader(text = stringResource(headerRes)) }
                items(contributors) { contributor -> ContributorRow(contributor) }
            }
        }
    }
}

@Composable
private fun ContributorRow(contributor: Contributor) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://github.com/${contributor.login}".toUri())
                )
            }
            .padding(horizontal = Dimensions.SpacingLarge, vertical = Dimensions.SpacingMediumLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(Dimensions.MinTouchTarget)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(Dimensions.SpacingMediumLarge)
                )
            }
        }

        Spacer(modifier = Modifier.width(Dimensions.SpacingLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contributor.name ?: contributor.login,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (contributor.note != null) {
                Text(
                    text = contributor.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
