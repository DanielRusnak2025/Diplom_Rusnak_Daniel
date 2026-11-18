package org.example.androidapp.ui.admin

internal fun auditActionLabel(action: String): String = when (action) {
    "REGISTER"             -> "Регистрация"
    "LOGIN"                -> "Вход"
    "LOGIN_FAILED"         -> "Неудачный вход"
    "TOKEN_REFRESH"        -> "Обновление токена"
    "TOKEN_REUSE"          -> "Повторное использование токена"
    "LOGOUT"               -> "Выход"
    "PASSWORD_CHANGED"     -> "Смена пароля"
    "ARTICLE_HIDDEN"       -> "Статья скрыта"
    "ARTICLE_UNHIDDEN"     -> "Статья показана"
    "COMPLAINTS_DISMISSED" -> "Жалобы отклонены"
    "COMPLAINT_DISMISSED"  -> "Жалоба отклонена"
    "USER_ROLE_CHANGED"    -> "Роль изменена"
    else                   -> action
}
