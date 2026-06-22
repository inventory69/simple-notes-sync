package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import dev.dettmer.simplenotes.markdown.MarkdownPreview
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.theme.Dimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val VERSION_REGEX = Regex("""\[(.+?)]\s*-\s*(.+)""")

private fun parseVersionHeader(text: String): Pair<String, String>? =
    VERSION_REGEX.find(text)?.let { it.groupValues[1] to it.groupValues[2].trim() }

@Composable
private fun VersionCard(header: MarkdownBlock.Heading, blocks: List<MarkdownBlock>) {
    val parsed = parseVersionHeader(header.text)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.SpacingLarge, vertical = Dimensions.SpacingSmall)
    ) {
        Column(modifier = Modifier.padding(Dimensions.SpacingLarge)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "v${parsed?.first ?: header.text}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (parsed != null) {
                    Text(
                        text = parsed.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SpacingMedium))
            MarkdownPreview(blocks = blocks, scrollEnabled = false, compactHeaders = true)
        }
    }
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalConfiguration.current.locales[0].language
    val versions = remember { mutableStateListOf<Pair<MarkdownBlock.Heading, List<MarkdownBlock>>>() }

    LaunchedEffect(lang) {
        versions.clear()
        withContext(Dispatchers.IO) {
            val file = if (lang == "de") "changelog.de.md" else "changelog.md"
            val text = context.assets.open(file).bufferedReader().readText()
            val chunks = text.split(Regex("(?m)^## ")).drop(1).map { "## $it" }
            for (chunk in chunks) {
                val blocks = MarkdownEngine.parse(chunk)
                val h2 = blocks.filterIsInstance<MarkdownBlock.Heading>()
                    .firstOrNull { it.level == 2 } ?: continue
                val body = blocks.drop(1).filter { it !is MarkdownBlock.HorizontalRule }
                withContext(Dispatchers.Main) { versions.add(h2 to body) }
            }
        }
    }

    SettingsScaffold(title = stringResource(R.string.about_changelog_title), onBack = onBack) { padding ->
        if (versions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(versions) { (header, blocks) ->
                    VersionCard(header = header, blocks = blocks)
                }
            }
        }
    }
}
