package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The tenant's delay-code list, IATA by default. */
@Entity
@Table(name = "delay_codes", schema = "ops")
@Getter
@Setter
public class DelayCode extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
