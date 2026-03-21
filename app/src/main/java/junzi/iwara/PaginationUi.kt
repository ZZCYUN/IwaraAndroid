package junzi.iwara

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaginationBar(
    currentPage: Int,
    totalCount: Int,
    pageSize: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageSize <= 0) return

    val derivedTotalPages = ((totalCount + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safeCurrentPage = currentPage.coerceAtLeast(0)
    val displayTotalPages = max(derivedTotalPages, safeCurrentPage + 1)
    if (displayTotalPages <= 1) return

    var pageInput by rememberSaveable(currentPage, totalCount, pageSize) { mutableStateOf((safeCurrentPage + 1).toString()) }
    val visiblePages = remember(safeCurrentPage, displayTotalPages) {
        buildList {
            add(0)
            for (page in max(0, safeCurrentPage - 2)..min(displayTotalPages - 1, safeCurrentPage + 2)) {
                add(page)
            }
            add(displayTotalPages - 1)
        }.distinct().sorted()
    }

    fun submitPageJump() {
        val targetPage = pageInput.toIntOrNull()?.takeIf { it > 0 } ?: return
        pageInput = targetPage.toString()
        val zeroBasedPage = targetPage - 1
        if (zeroBasedPage != safeCurrentPage) {
            onPageSelected(zeroBasedPage)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.label_page_indicator, safeCurrentPage + 1, displayTotalPages),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.label_page_total_count, totalCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { if (safeCurrentPage > 0) onPageSelected(0) },
                enabled = safeCurrentPage > 0,
                label = { Text(stringResource(R.string.action_first_page)) },
            )
            AssistChip(
                onClick = { if (safeCurrentPage > 0) onPageSelected(safeCurrentPage - 1) },
                enabled = safeCurrentPage > 0,
                label = { Text(stringResource(R.string.action_previous_page)) },
            )
            var previousPage: Int? = null
            visiblePages.forEach { page ->
                if (previousPage != null && page - previousPage!! > 1) {
                    Text(
                        text = "...",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = page == safeCurrentPage,
                    onClick = { onPageSelected(page) },
                    label = { Text((page + 1).toString()) },
                )
                previousPage = page
            }
            AssistChip(
                onClick = { onPageSelected(safeCurrentPage + 1) },
                enabled = true,
                label = { Text(stringResource(R.string.action_next_page)) },
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = pageInput,
                onValueChange = { input ->
                    pageInput = input.filter(Char::isDigit)
                },
                modifier = Modifier.width(136.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.label_jump_to_page)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submitPageJump() }),
            )
            AssistChip(
                onClick = ::submitPageJump,
                enabled = pageInput.isNotBlank(),
                label = { Text(stringResource(R.string.action_go_to_page)) },
            )
        }
    }
}
