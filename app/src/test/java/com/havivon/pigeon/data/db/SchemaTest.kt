package com.havivon.pigeon.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schemas Room exported for each version are committed, so the auto-migrations can be generated. Version 3
 * only adds the two tables of the user's categories: every earlier table is exactly as it was, which is what makes
 * the automatic migration from 2 safe (no data is touched).
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
}
