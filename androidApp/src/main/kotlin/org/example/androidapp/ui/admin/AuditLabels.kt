package org.example.androidapp.ui.admin

internal fun auditActionLabel(action: String): String = when (action) {
    "REGISTER"             -> "Registration"
    "LOGIN"                -> "Login"
    "LOGIN_FAILED"         -> "Failed login"
    "TOKEN_REFRESH"        -> "Token refresh"
    "TOKEN_REUSE"          -> "Token reuse"
    "LOGOUT"               -> "Logout"
    "PASSWORD_CHANGED"     -> "Password changed"
    "ARTICLE_HIDDEN"       -> "Article hidden"
    "ARTICLE_UNHIDDEN"     -> "Article unhidden"
    "COMPLAINTS_DISMISSED" -> "Complaints dismissed"
    "COMPLAINT_DISMISSED"  -> "Complaint dismissed"
    "USER_ROLE_CHANGED"    -> "Role changed"
    else                   -> action
}
