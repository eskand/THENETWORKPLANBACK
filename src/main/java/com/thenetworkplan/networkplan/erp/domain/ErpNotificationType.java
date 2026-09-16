package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One statutory or contractual notification, with the clock that applies. */
@Entity
@Table(name = "erp_notification_types", schema = "safety")
@Getter
@Setter
public class ErpNotificationType extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "min_level", nullable = false)
    private short minLevel;

    @Column(name = "target", nullable = false)
    private String target;

    /**
     * The deadline as the text writes it: "Immediately", "72 hours",
     * "Before arrival". Not a number of hours — a deadline that cannot be
     * counted in hours must not pretend it can.
     */
    @Column(name = "within_label", nullable = false)
    private String withinLabel;

    @Column(name = "basis", nullable = false)
    private String basis;

    @Column(name = "note", nullable = false)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
