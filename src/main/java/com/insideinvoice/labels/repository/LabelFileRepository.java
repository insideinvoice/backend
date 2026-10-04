package com.insideinvoice.labels.repository;

import com.insideinvoice.labels.entity.LabelFile;
import com.insideinvoice.labels.enums.LabelKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LabelFileRepository extends JpaRepository<LabelFile, LabelFile.LabelFileKey> {

    Optional<LabelFile> findByLabelIdAndLabelType(Long labelId, LabelKind labelType);
}
