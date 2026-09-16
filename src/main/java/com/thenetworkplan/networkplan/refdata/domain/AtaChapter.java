package com.thenetworkplan.networkplan.refdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One chapter of ATA 100 / iSpec 2200.
 *
 * <p>Reference, not operator data: the numbering is the same in every
 * maintenance manual and every MEL in the industry. A defect stores the
 * chapter; the screen reads the name from here. A defect filed under the wrong
 * chapter then shows the wrong system, which is exactly what should happen.
 */
@Entity
@Table(name = "ata_chapters", schema = "refdata")
@Getter
@Setter
public class AtaChapter {

    @Id
    @Column(name = "chapter", nullable = false)
    private String chapter;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ata_group", nullable = false)
    private String ataGroup;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
