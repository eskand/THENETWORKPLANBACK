package com.thenetworkplan.networkplan.refdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * One answer, and the level it imposes.
 *
 * <p>A level of zero does not mean "nothing": it means "does not raise".
 */
@Entity
@Table(name = "erp_question_options", schema = "refdata")
@IdClass(ErpQuestionOption.Key.class)
@Getter
@Setter
public class ErpQuestionOption {

    @Id
    @Column(name = "question_code", nullable = false)
    private String questionCode;

    @Id
    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Composite key: the question and the answer's value. */
    public record Key(String questionCode, String value) implements Serializable {

        public Key() {
            this(null, null);
        }
    }
}
