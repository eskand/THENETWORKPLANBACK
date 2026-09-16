package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One member of staff who does not fly.
 *
 * <p>{@code crew.persons} carries the flight and cabin crew. The accountable
 * manager, the post holders, dispatch, the OCC, maintenance and the station are
 * not there, and the crisis console needs a <em>name</em> against each cell —
 * nobody calls "Post Holder — Ground Operations" at three in the morning.
 */
@Entity
@Table(name = "ground_staff", schema = "safety")
@Getter
@Setter
public class GroundStaff extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /**
     * The exact job title. The crisis console resolves a cell through this
     * string: change it here and a cell is left without a lead — which the
     * screen shows in red rather than passing over in silence.
     */
    @Column(name = "role_title", nullable = false)
    private String roleTitle;

    @Column(name = "staff_group", nullable = false)
    private String staffGroup;

    @Column(name = "base_icao")
    private String baseIcao;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
