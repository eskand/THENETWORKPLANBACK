package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One rehearsal of the emergency response plan.
 *
 * <p><b>A plan that is never exercised is not a plan.</b> This register is what
 * an auditor asks to see: what was rehearsed, by how many people, what it found
 * and what changed because of it.
 *
 * <p><b>A closed exercise carries its lessons.</b> The database refuses to close
 * one with no participants and no lessons — an exercise closed empty was not
 * debriefed, it was abandoned, and the two must not look alike in a record the
 * authority reads.
 */
@Entity
@Table(name = "erp_exercises", schema = "safety")
@Getter
@Setter
public class ErpExercise extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private ErpPlan plan;

    /** Table-top, communications, full-scale — they test different things. */
    @Column(name = "exercise_type", nullable = false)
    private String exerciseType;

    @Column(name = "scenario", nullable = false)
    private String scenario;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    /**
     * The level rehearsed.
     *
     * <p>A communications exercise does not test the same organisation as a
     * full-scale one, and a register that does not say which level was played
     * cannot show that the top of the plan has ever been tried.
     */
    @Column(name = "level")
    private Short level;

    @Column(name = "participants", nullable = false)
    private int participants;

    @Column(name = "findings", nullable = false)
    private int findings;

    @Column(name = "status", nullable = false)
    private String status = "PLANNED";

    @Column(name = "lessons")
    private String lessons;

    public boolean isPlanned() {
        return "PLANNED".equals(status);
    }
}
