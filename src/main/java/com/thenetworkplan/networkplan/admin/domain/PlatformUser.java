package com.thenetworkplan.networkplan.admin.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One person who may use the platform.
 *
 * <p><b>Two roles, and they answer different questions.</b> {@code role} says
 * which module a person works in. {@code camoRole} says whether they are inside
 * the continuing airworthiness organisation — and that is what carries the
 * authority to declare an aircraft AOG.
 *
 * <p>The authority belongs to the department, not to a grade: a planner who
 * finds a reason to stop a tail must be able to stop it without waiting for a
 * post holder. The control is not permission, it is that every change is named,
 * timestamped and audited.
 */
@Entity
@Table(name = "users", schema = "platform")
@Getter
@Setter
public class PlatformUser extends BaseEntity {

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "initials")
    private String initials;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "camo_role")
    private String camoRole;

    @Column(name = "email")
    private String email;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
