package com.thenetworkplan.networkplan.crew;

import static org.assertj.core.api.Assertions.assertThat;

import com.thenetworkplan.networkplan.crew.domain.CrewRole;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.dto.DocumentValidity;
import com.thenetworkplan.networkplan.crew.dto.PersonDto;
import com.thenetworkplan.networkplan.crew.mapper.CrewMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Crew Management v226.211 montre l'ancienneté (« #85 »), la date d'embauche
 * (tuile « New hires (90d) ») et les coordonnées de la fiche : téléphone, e-mail,
 * contact d'urgence. Audit du 23/09 : le dossier ne les portait pas. Absentes,
 * elles restent nulles — l'écran écrit « — », rien n'est inventé.
 */
class PersonFileFieldsTest {

    private final CrewMapper mapper = new CrewMapper();

    private Person person() {
        Person p = new Person();
        p.setId(UUID.randomUUID());
        p.setStaffNo("CC007");
        p.setFirstName("S.");
        p.setLastName("Ammar");
        p.setMainRole(CrewRole.CABIN);
        p.setBaseIcao("DTTA");
        return p;
    }

    @Test
    void theCrewFileCarriesSeniorityHireDateAndContacts() {
        Person p = person();
        p.setSeniorityRank(85);
        p.setHireDate(LocalDate.of(2023, 3, 28));
        p.setPhone("+216 71 000 000");
        p.setEmail("s.ammar@thenetworkplan.com");
        p.setEmergencyContact("R. Ammar (spouse) +216 98 000 000");

        PersonDto dto = mapper.toDto(p, DocumentValidity.VALID, List.of(), 0, 0, 0, null);

        assertThat(dto.seniorityRank()).isEqualTo(85);
        assertThat(dto.hireDate()).isEqualTo(LocalDate.of(2023, 3, 28));
        assertThat(dto.phone()).isEqualTo("+216 71 000 000");
        assertThat(dto.email()).isEqualTo("s.ammar@thenetworkplan.com");
        assertThat(dto.emergencyContact()).isEqualTo("R. Ammar (spouse) +216 98 000 000");
    }

    @Test
    void aFileWithoutThemReadsNullNotAnInventedValue() {
        PersonDto dto = mapper.toDto(person(), DocumentValidity.VALID, List.of(), 0, 0, 0, null);

        assertThat(dto.seniorityRank()).isNull();
        assertThat(dto.hireDate()).isNull();
        assertThat(dto.phone()).isNull();
        assertThat(dto.email()).isNull();
        assertThat(dto.emergencyContact()).isNull();
    }
}
