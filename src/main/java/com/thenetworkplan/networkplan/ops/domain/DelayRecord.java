package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A coded delay. Free text alone was the prototype's only delay reason. */
@Entity
@Table(name = "delay_records", schema = "ops")
@Getter
@Setter
public class DelayRecord extends BaseEntity {

    @Column(name = "leg_id", nullable = false)
    private UUID legId;

    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "sub_code")
    private String subCode;

    @Column(name = "remark")
    private String remark;
}
