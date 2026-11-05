package org.example.server.auth

import org.example.AuditLog
import org.example.User
import org.hibernate.Session

class AuditService {
    fun log(
        session: Session,
        actor: User?,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        details: String? = null
    ) {
        val entry = AuditLog().apply {
            this.actor = actor
            this.action = action
            this.targetType = targetType
            this.targetId = targetId
            this.details = details
        }
        session.persist(entry)
    }
}
