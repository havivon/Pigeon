package com.havivon.pigeon.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schemas Room exported for each version are committed, so the auto-migrations can be generated. Version 3
 * only adds the two tables of the user's categories, and version 4 a nullable colour column on one of them and a table for the
 * colours of the built-in folders: every earlier table and column is exactly as it was, which is what makes the automatic
 * migrations safe (no data is touched).
 */
class SchemaTest {

    private fun schema(version: Int): String {
        val path = "schemas/com.havivon.pigeon.data.db.PigeonDatabase/$version.json"
        return listOf(File(path), File("app/$path")).first { it.exists() }.readText()
    }

    /** Each table's CREATE statement, by table name (a light read of Room's JSON: no JSON library in the tests). */
    private fun tables(json: String): Map<String, String> =
        Regex("\"tableName\":\\s*\"([^\"]+)\",\\s*\"createSql\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(json)
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun `version 3 is exported with the categories and the moves by hand`() {
        val v3 = schema(3)
        assertTrue(Regex("\"version\":\\s*3\\b").containsMatchIn(v3))
        val t = tables(v3)
        assertTrue(t.getValue("custom_folders").contains("`name` TEXT NOT NULL"))
        assertTrue(t.getValue("custom_folders").contains("`position` INTEGER NOT NULL"))
        assertTrue(t.getValue("folder_overrides").contains("PRIMARY KEY(`key`)"))
        assertTrue(t.getValue("folder_overrides").contains("`customFolderId` INTEGER"))
    }

    @Test
    fun `the migration from 2 only adds tables`() {
        val v2 = tables(schema(2))
        val v3 = tables(schema(3))
        assertEquals(setOf("custom_folders", "folder_overrides"), v3.keys - v2.keys)
        for ((name, sql) in v2) assertEquals(name, sql, v3[name])
    }

    @Test
    fun `version 4 is exported with the colours of the categories`() {
        val v4 = schema(4)
        assertTrue(Regex("\"version\":\\s*4\\b").containsMatchIn(v4))
        val t = tables(v4)
        // Nullable, no default: a category made before has no colour
        assertTrue(t.getValue("custom_folders").contains("`argb` INTEGER)"))
        assertTrue(t.getValue("category_colors").contains("`folder` TEXT NOT NULL"))
        assertTrue(t.getValue("category_colors").contains("`argb` INTEGER NOT NULL"))
        assertTrue(t.getValue("category_colors").contains("PRIMARY KEY(`folder`)"))
    }

    @Test
    fun `the migration from 3 only adds a nullable column and a table`() {
        val v3 = tables(schema(3))
        val v4 = tables(schema(4))
        assertEquals(setOf("category_colors"), v4.keys - v3.keys)
        assertEquals(v3.keys, v4.keys - "category_colors")
        for ((name, sql) in v3) if (name != "custom_folders") assertEquals(name, sql, v4[name])
        // Every column of the old table is still there, in the same words; the new one is added at the end
        assertTrue(v4.getValue("custom_folders").startsWith(v3.getValue("custom_folders").removeSuffix(")")))
    }
}
