package com.thenetworkplan.networkplan.training.service;

import com.thenetworkplan.networkplan.training.dto.EnrolCommand;
import com.thenetworkplan.networkplan.training.dto.PersonTrainingDto;
import com.thenetworkplan.networkplan.training.dto.RecordCompletionCommand;
import com.thenetworkplan.networkplan.training.dto.SaveSessionCommand;
import com.thenetworkplan.networkplan.training.dto.TrainingComplianceDto;
import com.thenetworkplan.networkplan.training.dto.TrainingCourseDto;
import com.thenetworkplan.networkplan.training.dto.TrainingEnrolmentDto;
import com.thenetworkplan.networkplan.training.dto.TrainingRecordDto;
import com.thenetworkplan.networkplan.training.dto.TrainingSessionDto;
import com.thenetworkplan.networkplan.training.dto.UpdateEnrolmentCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DOM4 — the training programme: catalogue, calendar, files and compliance. */
public interface TrainingService {

    List<TrainingCourseDto> findCourses(UUID tenantId);

    List<TrainingSessionDto> findSessions(UUID tenantId, LocalDate from, LocalDate to);

    /** The compliance matrix: every active crew member against every mandatory course. */
    TrainingComplianceDto findCompliance(UUID tenantId);

    PersonTrainingDto findPersonFile(UUID tenantId, UUID personId);

    TrainingSessionDto createSession(UUID tenantId, SaveSessionCommand command);

    TrainingEnrolmentDto enrol(UUID tenantId, UUID sessionId, EnrolCommand command);

    /** Closing an enrolment; {@code ATTENDED} also writes the training record. */
    TrainingEnrolmentDto updateEnrolment(UUID tenantId, UUID enrolmentId, UpdateEnrolmentCommand command);

    TrainingRecordDto recordCompletion(UUID tenantId, RecordCompletionCommand command);
}
