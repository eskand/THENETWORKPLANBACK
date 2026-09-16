package com.thenetworkplan.networkplan.crew.repository;

import com.thenetworkplan.networkplan.crew.domain.CrewRole;
import com.thenetworkplan.networkplan.crew.domain.Person;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByTenantIdAndStaffNo(UUID tenantId, String staffNo);

    List<Person> findByTenantIdAndMainRoleAndActiveTrueOrderByLastNameAsc(UUID tenantId, CrewRole mainRole);

    Optional<Person> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * The crew list of the Crew Management screen.
     *
     * <p>The three filters are optional and are resolved in SQL rather than by
     * loading everyone and filtering in Java: {@code search} arrives already
     * lower-cased and surrounded by {@code %}, or null.
     */
    @Query("""
            select p from Person p
            where p.tenantId = :tenantId
              and (:activeOnly = false or p.active = true)
              and (:role is null or p.mainRole = :role)
              and (:search is null
                   or lower(p.staffNo) like :search
                   or lower(p.firstName) like :search
                   or lower(p.lastName) like :search)
            order by p.mainRole, p.lastName, p.firstName
            """)
    List<Person> search(@Param("tenantId") UUID tenantId,
                        @Param("role") CrewRole role,
                        @Param("search") String search,
                        @Param("activeOnly") boolean activeOnly);

    List<Person> findByTenantIdAndActiveTrueOrderByLastNameAsc(UUID tenantId);
}
