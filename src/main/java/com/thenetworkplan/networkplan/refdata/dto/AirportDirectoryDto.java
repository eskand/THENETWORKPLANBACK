package com.thenetworkplan.networkplan.refdata.dto;

import java.io.Serializable;
import java.util.List;

/**
 * One page of the aerodrome directory.
 *
 * <p><b>Why the count travels with the rows.</b> The directory holds nine and a
 * half thousand aerodromes and no screen can show them at once, so every answer
 * is a page. A page that did not say how many it came from would let an
 * operator read « 200 aerodromes in Europe » off a list that is really the
 * first two hundred of one thousand four hundred — and plan against it.
 *
 * @param rows     the aerodromes returned, operator's own stations first
 * @param matched  how many the search actually found
 * @param capped   true when {@code matched} is larger than {@code rows}, so the
 *                 screen can ask for a narrower search instead of implying the
 *                 list is complete
 */
public record AirportDirectoryDto(
        List<AirportRowDto> rows,
        long matched,
        boolean capped) implements Serializable {

    public static AirportDirectoryDto empty() {
        return new AirportDirectoryDto(List.of(), 0L, false);
    }
}
