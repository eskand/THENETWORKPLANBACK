package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un passager d'une etape, avec son document de voyage.
 *
 * <p>L'annexe fusionne la liste des passagers et la section « ID Documents » en
 * un seul tableau (TNPPaxDocs) : c'est la bonne forme, parce qu'un passager sans
 * document valide n'embarque pas.
 *
 * <p>La validite n'est pas une colonne. Elle se deduit de la date d'expiration
 * et de la date du vol : un booleen fige serait faux le lendemain.
 */
@Entity
@Table(name = "leg_passengers", schema = "ops")
@Getter
@Setter
public class LegPassenger extends BaseEntity {

    @Column(name = "leg_id", nullable = false)
    private UUID legId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "surname", nullable = false)
    private String surname;

    @Column(name = "given_name")
    private String givenName;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type")
    private TravelDocumentType documentType;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "document_expiry")
    private LocalDate documentExpiry;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "checked_in", nullable = false)
    private boolean checkedIn;

    @Column(name = "special_request")
    private String specialRequest;
}
