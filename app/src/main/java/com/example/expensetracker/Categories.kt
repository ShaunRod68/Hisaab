package com.example.expensetracker

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

data class Category(
    val key: String,
    val name: String,
    val color: Color,
    val sector: Int,          // position on screen: 0 top, 1 right upper, 2 right lower, 3 bottom, 4 left lower, 5 left upper
    val items: List<String>
)

/**
 * The six categories. Their positions are fixed so your thumb learns them; names, colors and items are
 * editable in Settings and saved on the phone.
 */
object Categories {
    val defaults = listOf(
        Category("misc", "Misc", Color(0xFFB58CFF), 0, listOf("Bills", "Recharge", "Health", "Other")),
        Category("commute", "Commute", Color(0xFF38D0FF), 1, listOf("Auto / cab", "Metro / train", "Petrol")),
        Category("shop", "Shopping", Color(0xFFFF6FAE), 2, listOf("Clothes", "Electronics", "Home")),
        Category("people", "People", Color(0xFFFFD23F), 3, listOf("Friends", "Family", "Other")),
        Category("junk", "Junk food", Color(0xFFFF8A3D), 4, listOf("Namkeen", "Noodles", "Biscuits", "Energy drink")),
        Category("kitchen", "Kitchen", Color(0xFF4EE08A), 5, listOf("Milk", "Curd", "Eggs", "Veg & fruit"))
    )

    @Volatile private var current: List<Category> = defaults
    val all: List<Category> get() = current

    val palette = listOf(
        0xFFB58CFF, 0xFF38D0FF, 0xFFFF6FAE, 0xFFFFD23F, 0xFFFF8A3D, 0xFF4EE08A,
        0xFFFF5A5F, 0xFF3D8BFF, 0xFF8BC34A, 0xFF00BFA5, 0xFFB0BEC5, 0xFFA1887F
    ).map { Color(it) }

    fun byKey(key: String): Category? = current.firstOrNull { it.key == key }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** Call once when the app starts. */
    fun load(ctx: Context) {
        val text = prefs(ctx).getString("categories_json", null) ?: return
        try {
            val arr = JSONArray(text)
            val list = ArrayList<Category>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val items = o.getJSONArray("items")
                list.add(
                    Category(
                        o.getString("key"), o.getString("name"), Color(o.getInt("color")), o.getInt("sector"),
                        List(items.length()) { items.getString(it) }
                    )
                )
            }
            if (list.size == defaults.size) current = list
        } catch (e: Exception) {
            // unreadable: keep the defaults
        }
    }

    fun save(ctx: Context, list: List<Category>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject().put("key", c.key).put("name", c.name).put("color", c.color.toArgb())
                    .put("sector", c.sector).put("items", JSONArray(c.items))
            )
        }
        prefs(ctx).edit().putString("categories_json", arr.toString()).apply()
        current = list
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit().remove("categories_json").apply()
        current = defaults
    }
}