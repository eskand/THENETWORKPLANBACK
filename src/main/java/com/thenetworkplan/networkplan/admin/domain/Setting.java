package com.thenetworkplan.networkplan.admin.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One operator parameter, and how it is offered on the Settings screen.
 *
 * <p><b>{@code readBy} names the service that consumes the key, or is null.</b>
 * It used to be mandatory, because the audit found settings that existed in the
 * prototype's interface and were read nowhere — {@code dispatch.contingency},
 * {@code dispatch.finalReserve} among them. Porting the prototype's Settings
 * module (annexe A4, {@code SETTINGS_SCHEMA} l. 66172-66338) brings those
 * fields in, all fifty-four of them, of which the prototype itself reads six.
 *
 * <p>Two ways out: invent a reader for each, or say so. Null says so. The
 * screen shows the row and marks it as consumed by nothing yet, and the list
 * of keys still to wire is one query away. A knob that silently does nothing
 * is the failure the audit named; a knob that says it does nothing yet is a
 * backlog item.
 *
 * <p><b>The presentation lives here too</b> — section, group, control, options,
 * bounds — because it is operator data, not code: two operators do not offer
 * the same time zones or the same default services. The prototype had no
 * choice but to hard-code it, having no server; its configuration therefore
 * lives in one browser's localStorage and follows nobody to another desk.
 */
@Entity
@Table(name = "settings", schema = "platform")
@Getter
@Setter
public class Setting extends BaseEntity {

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "setting_key", nullable = false)
    private String settingKey;

    @Column(name = "setting_value", nullable = false)
    private String settingValue;

    /** What "Reset this section" restores. Never changes once seeded. */
    @Column(name = "default_value", nullable = false)
    private String defaultValue;

    @Column(name = "value_type", nullable = false)
    private String valueType = "STRING";

    @Column(name = "unit")
    private String unit;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "description")
    private String description;

    /** Null when nothing reads this key yet. See the class comment. */
    @Column(name = "read_by")
    private String readBy;

    /** A derived or regulatory value is shown but cannot be edited here. */
    @Column(name = "editable", nullable = false)
    private boolean editable = true;

    /* --- how the field is offered ------------------------------------- */

    @Column(name = "section", nullable = false)
    private String section;

    @Column(name = "group_title", nullable = false)
    private String groupTitle;

    /**
     * TEXT, NUMBER, SELECT, SEGMENT, TOGGLE, COLOR, TAGS, TIME.
     *
     * <p>Distinct from {@link #valueType}: a yes/no is always a BOOLEAN, but it
     * is drawn as a switch here and could be drawn as a checkbox elsewhere.
     */
    @Column(name = "control", nullable = false)
    private String control;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private List<Option> options;

    @Column(name = "placeholder")
    private String placeholder;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @Column(name = "step_value")
    private BigDecimal stepValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * The row is hidden unless the setting named here holds {@link #showIfValue}.
     *
     * <p>A refresh interval has no meaning while automatic refresh is off, and
     * leaving it on screen invites someone to set a number that will not be used.
     */
    @Column(name = "show_if_key")
    private String showIfKey;

    @Column(name = "show_if_value")
    private String showIfValue;

    /** One entry of a SELECT or SEGMENT control. */
    public record Option(String value, String label) {
    }
}
