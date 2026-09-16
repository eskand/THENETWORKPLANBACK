package com.thenetworkplan.networkplan.training.mapper;

import com.thenetworkplan.networkplan.training.domain.TrainingCourse;
import com.thenetworkplan.networkplan.training.domain.TrainingEnrolment;
import com.thenetworkplan.networkplan.training.domain.TrainingRecord;
import com.thenetworkplan.networkplan.training.domain.TrainingSession;
import com.thenetworkplan.networkplan.training.dto.TrainingCourseDto;
import com.thenetworkplan.networkplan.training.dto.TrainingEnrolmentDto;
import com.thenetworkplan.networkplan.training.dto.TrainingRecordDto;
import com.thenetworkplan.networkplan.training.dto.TrainingSessionDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TrainingMapper {

    public TrainingCourseDto toDto(TrainingCourse course) {
        return new TrainingCourseDto(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getCategory().name(),
                course.getValidityMonths(),
                course.isMandatory(),
                course.getAuthorityRef());
    }

    /**
     * @param enrolments the enrolments of this session, already loaded in one
     *                   query by the service; {@code booked} is their count and
     *                   not a second query
     */
    public TrainingSessionDto toDto(TrainingSession session, List<TrainingEnrolmentDto> enrolments) {
        int booked = (int) enrolments.stream()
                .filter(enrolment -> !"CANCELLED".equals(enrolment.status()))
                .count();
        return new TrainingSessionDto(
                session.getId(),
                session.getCourse().getCode(),
                session.getCourse().getTitle(),
                session.getCourse().getCategory().name(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getLocation(),
                session.getCapacity(),
                booked,
                Math.max(0, session.getCapacity() - booked),
                session.getInstructor() == null ? null : session.getInstructor().fullName(),
                session.getStatus().name(),
                enrolments);
    }

    public TrainingEnrolmentDto toDto(TrainingEnrolment enrolment) {
        return new TrainingEnrolmentDto(
                enrolment.getId(),
                enrolment.getSession().getId(),
                enrolment.getPerson().getId(),
                enrolment.getPerson().getStaffNo(),
                enrolment.getPerson().fullName(),
                enrolment.getStatus().name(),
                enrolment.getScore());
    }

    /** @param status from the shared document rule, computed by the service */
    public TrainingRecordDto toDto(TrainingRecord record, String status) {
        return new TrainingRecordDto(
                record.getId(),
                record.getPerson().getId(),
                record.getCourse().getCode(),
                record.getCourse().getTitle(),
                record.getCourse().getCategory().name(),
                record.getCompletedOn(),
                record.getValidTo(),
                status,
                record.getScore(),
                record.getInstructor() == null ? null : record.getInstructor().fullName(),
                record.getReference());
    }
}
