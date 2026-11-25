package com.example.fishingnote.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EditKeyTest {
    @Test fun separator_in_input_does_not_make_different_inputs_equal() {
        // 区切りの文字を入れても、別の入力と同じにならない
        assertNotEquals(editKey("a, b", "c", ""), editKey("a", "b, c", ""))
        assertNotEquals(editKey(listOf("a", "b")), editKey(listOf("a, b")))
        assertNotEquals(editKey(null), editKey("null"))
        assertNotEquals(editKey(listOf(listOf("a"), listOf("b"))), editKey(listOf(listOf("a", "b"))))
    }

    @Test fun same_input_gives_same_key() {
        assertEquals(editKey("アジ", 1L, null, listOf(listOf("サバ", "", 2))), editKey("アジ", 1L, null, listOf(listOf("サバ", "", 2))))
    }
}
