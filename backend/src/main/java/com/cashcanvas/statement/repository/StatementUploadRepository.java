package com.cashcanvas.statement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cashcanvas.statement.entity.StatementUpload;
import com.cashcanvas.statement.entity.UploadStatus;

public interface StatementUploadRepository extends JpaRepository<StatementUpload, Long> {

    /** Ownership check in the query itself: another user's id simply returns empty -> 404. */
    Optional<StatementUpload> findByIdAndUserId(Long id, Long userId);

    List<StatementUpload> findByStatusIn(Collection<UploadStatus> statuses);
}
