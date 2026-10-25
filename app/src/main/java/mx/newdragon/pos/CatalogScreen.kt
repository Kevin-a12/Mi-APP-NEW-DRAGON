package mx.newdragon.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable fun CatalogBrowser(catalog: List<CatalogItem>, disabled: Boolean, onSelect: (CatalogItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Todas") }
    val categories = remember(catalog) { listOf("Todas") + catalog.filter { it.product.active }.map { it.product.category }.distinct() }
    val matches = remember(catalog, query, category) { catalog.filter { it.product.active && MenuSearch.matches(it.product, query, category) } }
    val gridState = rememberLazyGridState()
    val largeText = LocalDensity.current.fontScale > 1.3f
    LaunchedEffect(query, category) { gridState.scrollToItem(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 360.dp && !largeText
        val inlineFilters = maxWidth >= 540.dp && !largeText
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable fun search(modifier: Modifier = Modifier) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar nombre o descripción") },
                    singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Limpiar búsqueda") } },
                    modifier = modifier, shape = MaterialTheme.shapes.small)
            }
            if (inlineFilters) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    search(Modifier.weight(1f))
                    Box(Modifier.widthIn(max = 210.dp)) { Choice("Categoría", category, categories) { category = it } }
                }
                Text("${matches.size} ${if (matches.size == 1) "platillo" else "platillos"}", style = MaterialTheme.typography.labelMedium, color = DragonMuted)
            } else {
                search(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Choice("Categoría", category, categories) { category = it } }
                    Text("${matches.size} ${if (matches.size == 1) "platillo" else "platillos"}", style = MaterialTheme.typography.labelMedium, color = DragonMuted)
                }
            }
            if (category != "Todas" || query.isNotBlank()) {
                TextButton({ category = "Todas"; query = "" }, colors = ButtonDefaults.textButtonColors(contentColor = DragonInk)) { Text("Quitar filtros") }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories.take(6), key = { it }) { option ->
                        FilterChip(category == option, { category = option }, label = { Text(option) }, enabled = !disabled)
                    }
                }
            }
            if (matches.isEmpty()) Info("No hay coincidencias. Prueba otro nombre o quita los filtros.")
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (twoColumns) 2 else 1), state = gridState, modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                gridItems(matches, key = { it.product.id }) { item ->
                    Card(onClick = { onSelect(item) }, enabled = !disabled, shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, Color(0xFFECEEE9)),
                        colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 162.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.product.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = DragonGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.product.name, style = MaterialTheme.typography.titleMedium, maxLines = if (largeText) 4 else 2,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 44.dp))
                            Text(item.product.description.ifBlank { "Selecciona para ver presentaciones" },
                                style = MaterialTheme.typography.bodySmall, color = DragonMuted, maxLines = if (largeText) 4 else 2,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 36.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text((if (item.variants.size > 1) "Desde " else "") + Money.show(item.variants.minOfOrNull { it.priceCents } ?: 0),
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Elegir", color = DragonRed, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
