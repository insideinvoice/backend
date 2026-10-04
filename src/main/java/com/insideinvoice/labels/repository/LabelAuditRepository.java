package com.insideinvoice.labels.repository;

import com.insideinvoice.labels.entity.LabelAudit;
import com.insideinvoice.labels.enums.LabelKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabelAuditRepository extends JpaRepository<LabelAudit, Long> {

    List<LabelAudit> findByLabelIdAndLabelTypeOrderByCreatedAtDesc(Long labelId, LabelKind labelType);
}
