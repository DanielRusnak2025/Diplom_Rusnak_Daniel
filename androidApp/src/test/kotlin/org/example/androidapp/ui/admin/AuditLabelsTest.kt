package org.example.androidapp.ui.admin

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuditLabelsTest {

    @Test
    fun `auditActionLabel maps every server action code to Russian`() {
        val serverCodes = listOf(
            "REGISTER",
            "LOGIN",
            "LOGIN_FAILED",
            "TOKEN_REFRESH",
            "TOKEN_REUSE",
            "LOGOUT",
            "PASSWORD_CHANGED",
            "ARTICLE_HIDDEN",
            "ARTICLE_UNHIDDEN",
            "COMPLAINTS_DISMISSED",
            "USER_ROLE_CHANGED",
        )
        for (code in serverCodes) {
            val label = auditActionLabel(code)
            assertNotEquals(code, label,
                "auditActionLabel must translate $code to Russian — still returning the code itself")
            assertTrue(
                label.any { c -> c in 'А'..'я' || c == 'ё' || c == 'Ё' },
                "Label for $code must contain Cyrillic characters, got: \"$label\""
            )
        }
    }
}
