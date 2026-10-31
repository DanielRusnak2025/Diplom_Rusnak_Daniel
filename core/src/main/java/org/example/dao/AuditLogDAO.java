package org.example.dao;

import org.example.AuditLog;

public interface AuditLogDAO {
    AuditLog save(AuditLog log);
}
