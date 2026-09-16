package com.thenetworkplan.networkplan.camoadmin.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One document of the airworthiness record.
 *
 * <p>An expired document grounds an aircraft as surely as a defect does. The
 * days remaining are computed at read time, never stored — a stored countdown
 * is wrong the morning after it is written.
 *
 * <p>A null {@code expiryDate} means <em>no expiry</em> (an AMM, an IPC), not
 * <em>unknown</em>. The two must not look alike on a screen an auditor reads.
 */
@Entity
@Table(name = "documents", schema = "camo")
@Getter
@Setter
public class CamoDocument extends BaseEntity {

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "reference")
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_id")
    private Aircraft aircraft;

    @Column(name = "component_id")
    private UUID componentId;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "issued_by")
    private String issuedBy;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "status", nullable = false)
    private String status = "CURRENT";

    @Column(name = "notes")
    private String notes;
}
