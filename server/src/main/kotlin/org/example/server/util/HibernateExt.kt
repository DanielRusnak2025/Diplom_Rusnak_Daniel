package org.example.server.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.util.HibernateUtil
import org.hibernate.Session

suspend fun <T> withNewTransaction(block: (Session) -> T): T =
    withContext(Dispatchers.IO) {
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
    }
