package com.example.expensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val positions = listOf("Top", "Right, upper", "Right, lower", "Bottom", "Left, lower", "Left, upper")
private const val MAX_ITEMS = 8

// What you are editing, kept separate until you press Save
private class DraftItem(val orig: String?, name: String) {
    var name by mutableStateOf(name)
}

private class DraftCat(val key: String, val sector: Int, name: String, color: Color, items: List<DraftItem>) {
    var name by mutableStateOf(name)
    var color by mutableStateOf(color)
    val items = mutableStateListOf<DraftItem>().also { it.addAll(items) }
}

private fun draftsFromSaved(): List<DraftCat> = Categories.all.map { c ->
    DraftCat(c.key, c.sector, c.name, c.color, c.items.map { DraftItem(it, it) })
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts = remember { mutableStateListOf<DraftCat>().also { it.addAll(draftsFromSaved()) } }
    var status by remember { mutableStateOf("") }

    fun save() {
        for (c in drafts) {
            val names = c.items.map { it.name.trim() }
            if (c.name.isBlank()) { status = "Every category needs a name"; return }
            if (names.isEmpty()) { status = "${c.name} needs at least one item"; return }
            if (names.any { it.isEmpty() }) { status = "Fill in or remove the empty item in ${c.name}"; return }
            if (names.map { it.lowercase() }.toSet().size != names.size) {
                status = "${c.name} has two items with the same name"; return
            }
        }
        val db = Db.get(ctx)
        scope.launch {
            withContext(Dispatchers.IO) {
                drafts.forEach { c ->
                    val renames = c.items
                        .filter { it.orig != null && it.orig != it.name.trim() }
                        .map { it.orig!! to it.name.trim() }
                    if (renames.isNotEmpty()) db.renameItems(c.key, renames)
                }
            }
            Categories.save(
                ctx,
                drafts.map { c -> Category(c.key, c.name.trim(), c.color, c.sector, c.items.map { it.name.trim() }) }
            )
            drafts.clear()
            drafts.addAll(draftsFromSaved())
            TxBus.tick.value++
            status = "Saved"
        }
    }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("\u2039 Settings") }
        Text("Categories", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The six places on screen are fixed so your thumb learns them. You can rename each one, change its " +
                    "color and edit its items. Renaming an item also renames it in everything you have already sorted.",
            style = MaterialTheme.typography.bodySmall
        )

        drafts.forEach { cat -> CategoryCard(cat) }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save() }) { Text("Save changes") }
            OutlinedButton(onClick = {
                Categories.reset(ctx)
                drafts.clear()
                drafts.addAll(draftsFromSaved())
                TxBus.tick.value++
                status = "Back to the original categories"
            }) { Text("Reset") }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(cat: DraftCat) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(positions[cat.sector], style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).background(cat.color, CircleShape))
                OutlinedTextField(
                    value = cat.name, onValueChange = { cat.name = it }, label = { Text("Category name") },
                    singleLine = true, modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
            FlowRow(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Categories.palette.forEach { c ->
                    Box(
                        Modifier.size(30.dp).background(c, CircleShape)
                            .border(if (cat.color == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { cat.color = c }
                    )
                }
            }

            Text("Items, in the order they appear on the line", style = MaterialTheme.typography.labelMedium)
            cat.items.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = item.name, onValueChange = { item.name = it }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { val x = cat.items.removeAt(i); cat.items.add(i - 1, x) },
                        enabled = i > 0
                    ) { Text("\u2191") }
                    TextButton(
                        onClick = { val x = cat.items.removeAt(i); cat.items.add(i + 1, x) },
                        enabled = i < cat.items.size - 1
                    ) { Text("\u2193") }
                    TextButton(
                        onClick = { cat.items.removeAt(i) },
                        enabled = cat.items.size > 1
                    ) { Text("\u2715") }
                }
            }
            OutlinedButton(
                onClick = { cat.items.add(DraftItem(null, "")) },
                enabled = cat.items.size < MAX_ITEMS,
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("Add item") }
        }
    }
}