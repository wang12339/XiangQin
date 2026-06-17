package com.xiangqin.app.server

import org.junit.Assert.*
import org.junit.Test

class WebServerUtilsTest {

    @Test
    fun `escapedJson escapes special characters`() {
        val input = "hello \"world\"\n\t\\done"
        val result = escapedJson(input)
        assertEquals("hello \\\"world\\\"\\n\\t\\\\done", result)
    }

    @Test
    fun `escapedJson handles empty string`() {
        assertEquals("", escapedJson(""))
    }

    @Test
    fun `escapedJson handles no special chars`() {
        assertEquals("hello world", escapedJson("hello world"))
    }

    @Test
    fun `jsonMap builds correct JSON with strings`() {
        val result = jsonMap("key" to "value", "name" to "test")
        assertEquals("""{"key": "value","name": "test"}""", result)
    }

    @Test
    fun `jsonMap builds correct JSON with numbers`() {
        val result = jsonMap("count" to 42, "size" to 100L)
        assertEquals("""{"count": 42,"size": 100}""", result)
    }

    @Test
    fun `jsonMap builds correct JSON with booleans`() {
        val result = jsonMap("active" to true, "deleted" to false)
        assertEquals("""{"active": true,"deleted": false}""", result)
    }

    @Test
    fun `jsonMap escapes strings inside values`() {
        val result = jsonMap("msg" to "hello \"world\"")
        assertEquals("""{"msg": "hello \"world\""}""", result)
    }

    @Test
    fun `escapeJson extension works`() {
        val result = "test\"quote".escapeJson()
        assertEquals("test\\\"quote", result)
    }
}
