package com.thenetworkplan.networkplan.mel.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.MelItem;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.airworthiness.repository.MelItemRepository;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.mel.domain.MelLibraryItem;
import com.thenetworkplan.networkplan.mel.dto.CloseMelCommand;
import com.thenetworkplan.networkplan.mel.dto.MelEntryDto;
import com.thenetworkplan.networkplan.mel.dto.MelLibraryItemDto;
import com.thenetworkplan.networkplan.mel.dto.RaiseMelCommand;
import com.thenetworkplan.networkplan.mel.mapper.MelMapper;
import com.thenetworkplan.networkplan.mel.repository.MelLibraryRepository;
import com.thenetworkplan.networkplan.mel.service.MelRectificationRule;
import com.thenetworkplan.networkplan.mel.service.MelService;
import com.thenetworkplan.networkplan.techlog.domain.Defect;
import com.thenetworkplan.networkplan.techlog.repository.DefectRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MEL / CDL / HIL.
 *
 * <p>Two rules carry the module, and both answer audit findings. A deferral
 * always rests on a library line, so it always has a category and an interval —
 * no more {@code dueDate '—'}. And a deferral that requires a placard is
 * refused until the placard is declared fitted, because that is the condition
 * the MEL itself puts on the release.
 */
@Service
@Transactional(readOnly = true)
public class MelServiceImpl implements MelService {

    private final MelItemRepository melItemRepository;
    private final MelLibraryRepository libraryRepository;
    private final AircraftRepository aircraftRepository;
    private final MelRectificationRule rectificationRule;
    private final MelMapper mapper;
    private final DefectRepository defectRepository;

    public MelServiceImpl(MelItemRepository melItemRepository,
                          MelLibraryRepository libraryRepository,
                          AircraftRepository aircraftRepository,
                          MelRectificationRule rectificationRule,
                          MelMapper mapper,
                          DefectRepository defectRepository) {
        this.melItemRepository = melItemRepository;
        this.libraryRepository = libraryRepository;
        this.aircraftRepository = aircraftRepository;
        this.rectificationRule = rectificationRule;
        this.mapper = mapper;
        this.defectRepository = defectRepository;
    }

    @Override
    public List<MelLibraryItemDto> findLibrary(UUID tenantId, String icaoType, String ataChapter) {
        String type = (icaoType == null || icaoType.isBlank()) ? null : icaoType.trim().toUpperCase();
        String ata = (ataChapter == null || ataChapter.isBlank()) ? null : ataChapter.trim();
        return libraryRepository.findLibrary(tenantId, type, ata).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MelEntryDto> findOpen(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<MelItem> items = melItemRepository.findOpenForFleet(tenantId);
        Map<UUID, MelLibraryItem> library = libraryFor(tenantId, items);
        Map<UUID, String> raisedByDefect = defectRepository
                .findByTenantIdAndMelItemIdNotNull(tenantId).stream()
                .collect(Collectors.toMap(Defect::getMelItemId, Defect::getAtaChapter,
                        (first, second) -> first));

        return items.stream()
                .map(item -> mapper.toDto(item, library.get(item.getMelLibraryId()), now,
                        raisedByDefect.get(item.getId()),
                        raisedByDefect.containsKey(item.getId()) ? "Tech Log" : "CAMO"))
                .sorted(Comparator.comparingInt((MelEntryDto entry) -> rank(entry.dueStatus())).reversed()
                        .thenComparing(MelEntryDto::registration))
                .toList();
    }

    @Override
    public List<MelEntryDto> findForAircraft(UUID tenantId, UUID aircraftId, boolean openOnly) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<MelItem> items = openOnly
                ? melItemRepository.findOpenForAircraft(tenantId, aircraftId)
                : melItemRepository.findAllForAircraft(tenantId, aircraftId);
        Map<UUID, MelLibraryItem> library = libraryFor(tenantId, items);
        return items.stream()
                .map(item -> mapper.toDto(item, library.get(item.getMelLibraryId()), now))
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.FLEET, key = "#tenantId"),
            @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    })
    public MelEntryDto raise(UUID tenantId, RaiseMelCommand command) {
        Aircraft aircraft = aircraftRepository.findOneWithType(tenantId, command.aircraftId())
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft", command.aircraftId()));
        MelLibraryItem library = libraryRepository.findOne(tenantId, command.melLibraryItemId())
                .orElseThrow(() -> ResourceNotFoundException.of("MEL library item", command.melLibraryItemId()));

        if (library.getAircraftType() != null
                && !library.getAircraftType().getId().equals(aircraft.getAircraftType().getId())) {
            throw new BusinessRuleException("MEL_TYPE_MISMATCH",
                    "MEL line " + library.getItemRef() + " belongs to "
                            + library.getAircraftType().getIcaoType() + ", not to "
                            + aircraft.getAircraftType().getIcaoType());
        }

        boolean placardFitted = Boolean.TRUE.equals(command.placardFitted());
        if (library.isPlacardRequired() && !placardFitted) {
            throw new BusinessRuleException("MEL_PLACARD_REQUIRED",
                    "MEL line " + library.getItemRef() + " requires a placard before the item can be deferred");
        }

        OffsetDateTime raisedAt = OffsetDateTime.now(ZoneOffset.UTC);
        MelItem item = new MelItem();
        item.setTenantId(tenantId);
        item.setAircraft(aircraft);
        item.setMelLibraryId(library.getId());
        item.setReference(library.getItemRef());
        item.setMelCategory(library.getMelCategory());
        item.setTitle(library.getTitle());
        item.setLimitation(command.remark() == null || command.remark().isBlank()
                ? library.getLimitation()
                : library.getLimitation() + " — " + command.remark());
        item.setRaisedAt(raisedAt);
        item.setDueAt(rectificationRule.dueAt(library.getMelCategory(), library.getRectificationDays(), raisedAt));
        item.setRaisedBy(command.raisedBy());
        item.setPlacardFitted(placardFitted);
        // Category A with a required quantity above what is installed leaves the
        // aircraft unable to dispatch: that judgement is the MEL line's, and it
        // is carried, not re-decided here.
        item.setBlocksDispatch(library.getRequiredQuantity() != null
                && library.getInstalledQuantity() != null
                && library.getRequiredQuantity() >= library.getInstalledQuantity());

        MelItem saved = melItemRepository.save(item);
        return mapper.toDto(saved, library, raisedAt);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.FLEET, key = "#tenantId"),
            @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    })
    public MelEntryDto close(UUID tenantId, UUID melItemId, CloseMelCommand command) {
        MelItem item = melItemRepository.findById(melItemId)
                .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                .orElseThrow(() -> ResourceNotFoundException.of("MEL item", melItemId));
        if (!item.isOpen()) {
            throw new BusinessRuleException("MEL_ALREADY_CLOSED",
                    "MEL item " + item.getReference() + " was cleared on " + item.getClosedAt());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        item.setClosedAt(now);
        item.setClosedBy(command.closedBy());
        item.setLimitation(item.getLimitation() == null
                ? command.correctiveAction()
                : item.getLimitation() + " — cleared: " + command.correctiveAction());
        MelItem saved = melItemRepository.save(item);

        MelLibraryItem library = saved.getMelLibraryId() == null
                ? null
                : libraryRepository.findOne(tenantId, saved.getMelLibraryId()).orElse(null);
        return mapper.toDto(saved, library, now);
    }

    /** Library lines of the items in hand, in one query rather than one per item. */
    private Map<UUID, MelLibraryItem> libraryFor(UUID tenantId, List<MelItem> items) {
        Map<UUID, MelLibraryItem> byId = new HashMap<>();
        if (items.isEmpty()) {
            return byId;
        }
        for (MelLibraryItem library : libraryRepository.findLibrary(tenantId, null, null)) {
            byId.put(library.getId(), library);
        }
        return byId;
    }

    private static int rank(String dueStatus) {
        return switch (dueStatus) {
            case "PLANNED" -> 0;
            case "UNKNOWN" -> 1;
            case "DUE_SOON" -> 2;
            case "OVERDUE" -> 3;
            default -> 0;
        };
    }
}
