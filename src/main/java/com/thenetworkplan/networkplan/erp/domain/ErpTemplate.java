package com.thenetworkplan.networkplan.erp.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A communication template.
 *
 * <p>The body carries its tokens — {@code {FLIGHT}}, {@code {REG}},
 * {@code {POB}}. They are filled on the server: a half-filled template sent to
 * the press is worse than no template.
 */
@Entity
@Table(name = "erp_templates", schema = "safety")
@Getter
@Setter
public class ErpTemplate extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "min_level", nullable = false)
    private short minLevel;

    @Column(name = "audience", nullable = false)
    private String audience;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
