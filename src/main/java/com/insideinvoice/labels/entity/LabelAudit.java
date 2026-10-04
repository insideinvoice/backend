package com.insideinvoice.labels.entity;

import com.insideinvoice.labels.enums.LabelAuditAction;
import com.insideinvoice.labels.enums.LabelKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "label_audit", indexes = {
        @Index(name = "idx_label_audit_label", columnList = "label_id,label_type"),
        @Index(name = "idx_label_audit_business", columnList = "business_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabelAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "label_id", nullable = false)
    private Long labelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "label_type", nullable = false, length = 16)
    private LabelKind labelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 24)
    private LabelAuditAction action;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
