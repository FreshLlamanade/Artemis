package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.round;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.domain.enumeration.StatisticsView;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseManagementStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public StatisticsService(StatisticsRepository statisticsRepository, ParticipantScoreRepository participantScoreRepository, CourseRepository courseRepository,
            UserRepository userRepository) {
        this.statisticsRepository = statisticsRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Forwards the request to the repository, which returns a List<Map<String, Object>>. For week, month or year the map from the Repository contains a String with the column name,
     * "day" and "amount" and an Object being the value, either the date in the format "YYYY-MM-DD" or the amount of the findings. For day, the column names are "day" and "amount",
     * which then contains the date in the ZonedDateFormat as Integer and the amount as Long
     * It then collects the amounts in an array, depending on the span value, and returns it
     *
     * @param span DAY,WEEK,MONTH or YEAR depending on the active tab in the view
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @param graphType the type of graph the data should be fetched
     * @param entityId the entityId. Only set if we fetch value for the course statistics
     * @return an array, containing the values for each bar in the graph
     */
    public Integer[] getChartData(SpanType span, Integer periodIndex, GraphType graphType, StatisticsView view, Long entityId) {
        ZonedDateTime startDate;
        ZonedDateTime endDate;
        List<StatisticsEntry> outcome;
        Integer[] result = new Integer[0];
        ZonedDateTime now = ZonedDateTime.now();
        int lengthOfMonth;
        if (span != SpanType.MONTH) {
            result = new Integer[createSpanMap().get(span)];
            Arrays.fill(result, 0);
        }
        switch (span) {
            case DAY:
                startDate = now.minusDays(-periodIndex).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusDays(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(span, startDate, endDate, graphType, view, entityId);
                return this.statisticsRepository.mergeResultsIntoArrayForDay(outcome, result);
            case WEEK:
                startDate = now.minusWeeks(-periodIndex).minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusWeeks(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(span, startDate, endDate, graphType, view, entityId);
                return this.statisticsRepository.mergeResultsIntoArrayForWeek(outcome, result, startDate);
            case MONTH:
                startDate = now.minusMonths(1 - periodIndex).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusMonths(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                result = new Integer[(int) ChronoUnit.DAYS.between(startDate, endDate)];
                Arrays.fill(result, 0);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(span, startDate.plusDays(1), endDate, graphType, view, entityId);
                return this.statisticsRepository.mergeResultsIntoArrayForMonth(outcome, result, startDate.plusDays(1));
            case QUARTER:
                LocalDateTime localStartDate = now.toLocalDateTime().with(DayOfWeek.MONDAY);
                LocalDateTime localEndDate = now.toLocalDateTime().with(DayOfWeek.SUNDAY);
                ZoneId zone = now.getZone();
                startDate = localStartDate.atZone(zone).minusWeeks(11 + (12 * (-periodIndex))).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = periodIndex != 0 ? localEndDate.atZone(zone).minusWeeks(12 * (-periodIndex)).withHour(23).withMinute(59).withSecond(59)
                        : localEndDate.atZone(zone).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(span, startDate, endDate, graphType, view, entityId);
                return this.statisticsRepository.mergeResultsIntoArrayForQuarter(outcome, result, startDate);
            case YEAR:
                startDate = now.minusYears(1 - periodIndex).plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                lengthOfMonth = YearMonth.of(now.minusYears(-periodIndex).getYear(), now.minusYears(-periodIndex).getMonth()).lengthOfMonth();
                endDate = now.minusYears(-periodIndex).withDayOfMonth(lengthOfMonth).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(span, startDate, endDate, graphType, view, entityId);
                return this.statisticsRepository.mergeResultsIntoArrayForYear(outcome, result, startDate);
            default:
                return null;
        }
    }

    /**
     * A map to manage the spanTypes and the corresponding array length of the result
     */
    private Map<SpanType, Integer> createSpanMap() {
        Map<SpanType, Integer> spanMap = new HashMap<>();
        spanMap.put(SpanType.DAY, 24);
        spanMap.put(SpanType.WEEK, 7);
        spanMap.put(SpanType.QUARTER, 12);
        spanMap.put(SpanType.YEAR, 12);
        return spanMap;
    }

    /**
     * Get the average score of the course and all exercises for the course statistics
     *
     * @param courseId    the id of the course for which the data should be fetched
     * @return a custom CourseManagementStatisticsDTO, which contains the relevant data
     */
    public CourseManagementStatisticsDTO getCourseStatistics(Long courseId) {
        var courseManagementStatisticsDTO = new CourseManagementStatisticsDTO();
        var exercises = statisticsRepository.findExercisesByCourseId(courseId);
        var includedExercises = exercises.stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(Collectors.toSet());
        var averageScoreForCourse = participantScoreRepository.findAvgScore(includedExercises);
        var averageScoreForExercises = statisticsRepository.findAvgPointsForExercises(includedExercises);
        averageScoreForExercises.forEach(exercise -> {
            var roundedAverageScore = round(exercise.getAverageScore());
            exercise.setAverageScore(roundedAverageScore);
        });

        if (averageScoreForCourse != null && averageScoreForCourse > 0) {
            courseManagementStatisticsDTO.setAverageScoreOfCourse(round(averageScoreForCourse));
        }
        else {
            courseManagementStatisticsDTO.setAverageScoreOfCourse(0.0);
        }

        if (averageScoreForExercises.size() > 0) {
            courseManagementStatisticsDTO.setAverageScoresOfExercises(averageScoreForExercises);
        }
        else {
            courseManagementStatisticsDTO.setAverageScoresOfExercises(new ArrayList<>());
        }

        return courseManagementStatisticsDTO;
    }

    /**
     * Get the average score of the exercise
     *
     * @param exercise    the exercise for which the data should be fetched
     * @return a custom ExerciseManagementStatisticsDTO, which contains the relevant data
     */
    public ExerciseManagementStatisticsDTO getExerciseStatistics(Exercise exercise) throws EntityNotFoundException {
        var course = courseRepository.findByIdElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        var numberOfStudents = userRepository.countUserInGroup(course.getStudentGroupName());
        var exerciseManagementStatisticsDTO = new ExerciseManagementStatisticsDTO();
        var averageScoreForExercise = participantScoreRepository.findAvgScore(Set.of(exercise));
        averageScoreForExercise = averageScoreForExercise != null ? round(averageScoreForExercise) : 0.0;
        exerciseManagementStatisticsDTO.setAverageScoreOfExercise(averageScoreForExercise);
        /*
         * var maxPoints = exercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_AS_BONUS ? exercise.getMaxPoints() + exercise.getBonusPoints() :
         * exercise.getMaxPoints(); Set<User> usersOfCourse = new HashSet<>(); usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getStudentGroupName()));
         * usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName()));
         * usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getInstructorGroupName())); var scoreDTOS =
         * participantScoreService.calculateScores(Set.of(exercise), usersOfCourse, maxPoints); var scores = scoreDTOS.stream().map(scoreDTO ->
         * scoreDTO.scoreAchieved).collect(Collectors.toList());
         */
        var scores = participantScoreRepository.getScoreDistributionForExercise(exercise.getId());
        var scoreDistribution = new int[10];
        Arrays.fill(scoreDistribution, 0);

        scores.forEach(score -> {
            var index = (int) (score.getScore() / 10.0);
            if (index == 10) {
                scoreDistribution[9] += 1;
            }
            else {
                scoreDistribution[index] += 1;
            }
        });
        for (int i = 0; i < scoreDistribution.length; i++) {
            var amount = scoreDistribution[i];
            if (amount > 0) {
                scoreDistribution[i] = (amount * 100 / Math.toIntExact(numberOfStudents));
            }
        }

        exerciseManagementStatisticsDTO.setScoreDistribution(scoreDistribution);

        return exerciseManagementStatisticsDTO;
    }
}
