package com.insideinvoice.labels.entity;

import com.insideinvoice.labels.enums.LabelKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "label_files")
@IdClass(LabelFile.LabelFileKey.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabelFile {

    @Id
    @Column(name = "label_id")
    private Long labelId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "label_type", length = 16)
    private LabelKind labelType;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @Column(name = "pdf_bytes", nullable = false, columnDefinition = "bytea")
    private byte[] pdfBytes;

    @Column(name = "pdf_sha256", nullable = false, length = 64)
    private String pdfSha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabelFileKey implements Serializable {
        private Long labelId;
        private LabelKind labelType;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LabelFileKey other)) return false;
            return Objects.equals(labelId, other.labelId) && labelType == other.labelType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(labelId, labelType);
        }
    }
}
