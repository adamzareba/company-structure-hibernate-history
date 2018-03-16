package com.adamzareba.hibernate.history.config.listener;

import com.adamzareba.hibernate.history.model.audit.AuditRevisionEntity;

import org.hibernate.envers.RevisionListener;

public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity audit = (AuditRevisionEntity) revisionEntity;
        audit.setUsername("admin");
    }
}
