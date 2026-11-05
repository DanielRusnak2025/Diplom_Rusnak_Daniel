package org.example.server.util

import org.example.util.HibernateUtil
import org.hibernate.Session

fun <T> withNewTransaction(block: (Session) -> T): T =
    HibernateUtil.getSessionFactory().openSession().use { session ->
        val tx = session.beginTransaction()
        try {
            val result = block(session)
            tx.commit()
            result
        } catch (e: Exception) {
            if (tx.isActive) tx.rollback()
            throw e
        }
    }
