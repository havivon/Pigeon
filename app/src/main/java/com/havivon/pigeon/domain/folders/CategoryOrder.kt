package com.havivon.pigeon.domain.folders

import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder

/** One place in the list of categories the dropdown and Settings show: a built-in category or one of the user's. */
sealed interface CategorySlot {
    /** The key the slot is kept, dragged and exported under (see [CategoryOrder]). */
    val token: String

    data class BuiltIn(val folder: Folder) : CategorySlot {
        override val token: String get() = CategoryOrder.token(folder)
    }

    data class Custom(val folder: CustomFolder) : CategorySlot {
        override val token: String get() = CategoryOrder.token(folder)
    }
}

/**
 * The order of ALL the categories, built-in and the user's together, as the dropdown under the inbox header and
 * Settings → Categories list them. "All categories" is always first and Spam and "Manage categories…" always last,
 * so neither takes part.
 *
 * The order is kept as tokens: "b:Banks" for a built-in category (the enum name) and "c:12" for the user's category
 * with id 12. An empty list is the default order: the built-in categories as [Folder] declares them, then the user's in
 * the order they made them. A saved order that does not list a category (one made, or one added by a later version,
 * after the order was saved) puts it at the end; a token for a category that no longer exists is skipped.
 */
object CategoryOrder {
    /** The built-in categories that are placed in the list, in their default order: all but "All categories" and Spam. */
    val defaultBuiltIns: List<Folder> = Folder.entries.filter { it != Folder.All && it != Folder.Spam }

    fun token(folder: Folder): String = "b:${folder.name}"

    fun token(custom: CustomFolder): String = customToken(custom.id)

    fun customToken(id: Long): String = "c:$id"

    /** The categories in the order [saved] gives them (see the class comment for what is missing or stale). */
    fun resolve(saved: List<String>, custom: List<CustomFolder>): List<CategorySlot> {
        val all: Map<String, CategorySlot> = buildMap {
            defaultBuiltIns.forEach { put(token(it), CategorySlot.BuiltIn(it)) }
            custom.forEach { put(token(it), CategorySlot.Custom(it)) }
        }
        val out = LinkedHashMap<String, CategorySlot>()
        for (t in saved) all[t]?.let { out.putIfAbsent(t, it) }
        for ((t, slot) in all) out.putIfAbsent(t, slot)
        return out.values.toList()
    }

    /** The default order, as tokens. */
    fun default(custom: List<CustomFolder>): List<String> = resolve(emptyList(), custom).map { it.token }

    /** The ids of the user's categories in [tokens], in their order. */
    fun customIds(tokens: List<String>): List<Long> = tokens.mapNotNull { t -> t.removePrefix("c:").takeIf { it != t }?.toLongOrNull() }
}
