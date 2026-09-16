package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One position in the crisis organisation, with its holder and its deputy. */
@Entity
@Table(name = "erp_roles", schema = "safety")
@Getter
@Setter
public class ErpRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ErpPlan plan;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "role_title", nullable = false)
    private String roleTitle;

    @Column(name = "holder_user_id")
    private UUID holderUserId;

    /** A role with no deputy is a single point of failure, and the screen says so. */
    @Column(name = "deputy_user_id")
    private UUID deputyUserId;

    @Column(name = "phone")
    private String phone;

    @Column(name = "responsibilities")
    private String responsibilities;

    /**
     * What this cell covers, in plain words.
     *
     * <p>At three in the morning nobody remembers what « LEG » stands for.
     * « Insurer notification, legal privilege, advance payments, regulatory
     * correspondence » is the difference between a cell that acts and a cell
     * that asks who is supposed to act.
     */
    @Column(name = "scope")
    private String scope;

    /**
     * The colour this cell carries on the console.
     *
     * <p>Stored rather than chosen by the screen: the console, the crisis org
     * chart and the log all show the same cell and have to agree.
     */
    @Column(name = "colour")
    private String colour;

    /**
     * The job title that holds this cell.
     *
     * <p>The name comes from the personnel register, not from here: a title
     * cannot be telephoned at three in the morning, and a post nobody holds
     * has to show as unassigned rather than as a title.
     */
    @Column(name = "lead_role")
    private String leadRole;

    @Column(name = "call_order", nullable = false)
    private int callOrder = 1;
}
