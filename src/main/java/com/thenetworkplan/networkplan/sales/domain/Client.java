package com.thenetworkplan.networkplan.sales.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A client of the operator: corporate, broker, government, private, medical. */
@Entity
@Table(name = "clients", schema = "sales")
@Getter
@Setter
public class Client extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ClientKind kind = ClientKind.CORPORATE;

    @Column(name = "country_iso2")
    private String countryIso2;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "payment_terms")
    private String paymentTerms;

    /** Currency the client is normally billed in. A quote may still differ. */
    @Column(name = "currency", nullable = false)
    private String currency = "EUR";

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
