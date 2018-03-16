package com.adamzareba.hibernate.history.model.audit;

import com.adamzareba.hibernate.history.config.listener.AuditRevisionListener;

import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@RevisionEntity(AuditRevisionListener.class)
@Table(name = "REVINFO", schema = "audit")
@AttributeOverrides({
        @AttributeOverride(name = "timestamp", column = @Column(name = "REVTSTMP")),
        @AttributeOverride(name = "id", column = @Column(name = "REV"))})
@Getter
@Setter
public class AuditRevisionEntity extends DefaultRevisionEntity {

    @Column(name = "USERNAME", nullable = false)
    private String username;
}

