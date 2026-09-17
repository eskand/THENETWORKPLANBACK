package com.thenetworkplan.networkplan.ops.web;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.ops.dto.SaveLegPassengerCommand;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L'import CSV du manifeste passagers.
 *
 * <p>Le lecteur est celui de l'annexe (parseCsv, prototype l. 7215) : guillemets
 * doubles, guillemet echappe par doublement, fin de ligne normalisee. La
 * difference est l'endroit — le fichier est lu par le serveur, qui ecrit des
 * lignes en base, la ou l'annexe le lisait dans le navigateur et n'ecrivait que
 * dans {@code localStorage}.
 *
 * <p><b>Un fichier sans en-tete est refuse.</b> Deviner que la troisieme colonne
 * est un numero de passeport parce qu'elle y ressemble est la facon la plus sure
 * d'enregistrer un numero de visa a sa place.
 */
final class PassengerCsv {

    /** Les noms de colonnes acceptes, par champ. */
    private static final Map<String, List<String>> HEADERS = Map.of(
            "surname", List.of("surname", "last name", "lastname", "nom"),
            "givenName", List.of("given name", "givenname", "first name", "firstname", "prenom"),
            "documentType", List.of("document type", "doc type", "type"),
            "documentNumber", List.of("document number", "document n", "doc number", "number"),
            "documentExpiry", List.of("validity", "expiry", "expires", "valid until"),
            "nationality", List.of("nationality", "country"),
            "dateOfBirth", List.of("date of birth", "dob", "birth"));

    private PassengerCsv() {
    }

    static List<SaveLegPassengerCommand> parse(byte[] bytes) {
        List<List<String>> rows = read(new String(bytes, StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new BusinessRuleException("CSV_EMPTY", "The file has no rows");
        }
        Map<String, Integer> columns = mapHeader(rows.get(0));
        if (!columns.containsKey("surname")) {
            throw new BusinessRuleException("CSV_NO_SURNAME",
                    "The first line must name the columns, and one of them must be the surname");
        }

        List<SaveLegPassengerCommand> commands = new ArrayList<>(rows.size() - 1);
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            String surname = value(row, columns.get("surname"));
            if (surname == null || surname.isBlank()) {
                continue;
            }
            commands.add(new SaveLegPassengerCommand(
                    surname,
                    value(row, columns.get("givenName")),
                    documentType(value(row, columns.get("documentType"))),
                    value(row, columns.get("documentNumber")),
                    date(value(row, columns.get("documentExpiry")), index),
                    value(row, columns.get("nationality")),
                    date(value(row, columns.get("dateOfBirth")), index),
                    false,
                    null));
        }
        if (commands.isEmpty()) {
            throw new BusinessRuleException("CSV_NO_PASSENGER", "No passenger line carried a surname");
        }
        return commands;
    }

    /** parseCsv() de l'annexe, l. 7215 — meme automate, meme traitement des guillemets. */
    private static List<List<String>> read(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n');

        for (int index = 0; index < normalised.length(); index++) {
            char character = normalised.charAt(index);
            if (inQuotes) {
                if (character == '"') {
                    if (index + 1 < normalised.length() && normalised.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                inQuotes = true;
            } else if (character == ',' || character == ';') {
                row.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        row.add(field.toString());
        if (row.stream().anyMatch(cell -> !cell.isBlank())) {
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Integer> mapHeader(List<String> header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int index = 0; index < header.size(); index++) {
            String cell = header.get(index).trim().toLowerCase().replace('_', ' ').replace('°', ' ').trim();
            for (Map.Entry<String, List<String>> entry : HEADERS.entrySet()) {
                if (!columns.containsKey(entry.getKey()) && entry.getValue().contains(cell)) {
                    columns.put(entry.getKey(), index);
                }
            }
        }
        return columns;
    }

    private static String value(List<String> row, Integer index) {
        if (index == null || index >= row.size()) {
            return null;
        }
        String cell = row.get(index).trim();
        return cell.isEmpty() ? null : cell;
    }

    private static String documentType(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return switch (cleaned) {
            case "PASSPORT", "PASSEPORT" -> "PASSPORT";
            case "NATIONAL_ID", "ID", "ID_CARD", "CNI" -> "NATIONAL_ID";
            case "RESIDENCE_PERMIT", "RESIDENCE" -> "RESIDENCE_PERMIT";
            case "VISA" -> "VISA";
            case "CREW_CERTIFICATE", "CREW", "CMC" -> "CREW_CERTIFICATE";
            case "LAISSEZ_PASSER" -> "LAISSEZ_PASSER";
            default -> "OTHER";
        };
    }

    private static LocalDate date(String raw, int line) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replace('/', '-').replace('.', '-');
        try {
            return LocalDate.parse(cleaned);
        } catch (DateTimeParseException ignored) {
            // jj-mm-aaaa, la forme qu'un tableur europeen exporte
            String[] parts = cleaned.split("-");
            if (parts.length == 3 && parts[0].length() <= 2) {
                try {
                    return LocalDate.of(Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                } catch (RuntimeException ignoredToo) {
                    // tombe dans le refus ci-dessous
                }
            }
            throw new BusinessRuleException("CSV_BAD_DATE",
                    "Line " + line + ": \"" + raw + "\" is not a date the import can read "
                            + "(use YYYY-MM-DD)");
        }
    }
}
