package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.domain.enumeration.StatisticsView;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseManagementStatisticsDTO;

public class StatisticsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    Course course;

    TextExercise exercise;

    List<GraphType> artemisGraphs = Arrays.asList(GraphType.SUBMISSIONS, GraphType.ACTIVE_USERS, GraphType.LOGGED_IN_USERS, GraphType.RELEASED_EXERCISES, GraphType.EXERCISES_DUE,
            GraphType.CONDUCTED_EXAMS, GraphType.EXAM_PARTICIPATIONS, GraphType.EXAM_REGISTRATIONS, GraphType.ACTIVE_TUTORS, GraphType.CREATED_RESULTS,
            GraphType.CREATED_FEEDBACKS);

    List<GraphType> courseGraphs = Arrays.asList(GraphType.SUBMISSIONS, GraphType.ACTIVE_USERS, GraphType.RELEASED_EXERCISES, GraphType.EXERCISES_DUE, GraphType.CONDUCTED_EXAMS,
            GraphType.EXAM_PARTICIPATIONS, GraphType.EXAM_REGISTRATIONS, GraphType.ACTIVE_TUTORS, GraphType.CREATED_RESULTS, GraphType.CREATED_FEEDBACKS, GraphType.POSTS,
            GraphType.ANSWERED_POSTS);

    List<GraphType> exerciseGraphs = Arrays.asList(GraphType.SUBMISSIONS, GraphType.ACTIVE_USERS, GraphType.ACTIVE_TUTORS, GraphType.CREATED_RESULTS, GraphType.CREATED_FEEDBACKS,
            GraphType.POSTS, GraphType.ANSWERED_POSTS);

    @BeforeEach
    public void initTestCase() {
        database.addUsers(12, 10, 0, 10);

        course = database.addCourseWithOneModelingExercise();
        var now = ZonedDateTime.now();
        exercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.plusHours(1), course);
        course.addExercises(exercise);
        textExerciseRepository.save(exercise);
        Post post = new Post();
        post.setExercise(exercise);
        post.setContent("Test Student Question 1");
        post.setVisibleForStudents(true);
        post.setCreationDate(ZonedDateTime.now().minusSeconds(11));
        post.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        postRepository.save(post);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now().minusSeconds(10));
        answerPost.setPost(post);
        answerPostRepository.save(answerPost);

        // one submission today
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.submissionDate(ZonedDateTime.now().minusSeconds(1));
        var submission = database.addSubmission(exercise, textSubmission, "student1");
        database.addResultToSubmission(submission, AssessmentType.MANUAL);

        for (int i = 2; i <= 12; i++) {
            textSubmission = new TextSubmission();
            textSubmission.submissionDate(ZonedDateTime.now().minusMonths(i - 1).withDayOfMonth(10));
            submission = database.addSubmission(exercise, textSubmission, "student" + i);
            database.addResultToSubmission(submission, AssessmentType.MANUAL);
        }
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForDayEachGraph() throws Exception {

        SpanType span = SpanType.DAY;
        for (GraphType graph : artemisGraphs) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(24);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForWeekEachGraph() throws Exception {

        SpanType span = SpanType.WEEK;
        for (GraphType graph : artemisGraphs) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(7);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForMonthEachGraph() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        SpanType span = SpanType.MONTH;
        for (GraphType graph : artemisGraphs) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(YearMonth.of(now.getYear(), now.minusMonths(1 - periodIndex).plusDays(1).getMonth()).lengthOfMonth());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForQuarterEachGraph() throws Exception {
        SpanType span = SpanType.QUARTER;
        for (GraphType graph : artemisGraphs) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(12);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForYearEachGraph() throws Exception {

        SpanType span = SpanType.YEAR;
        for (GraphType graph : artemisGraphs) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(12);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetChartDataForCourse() throws Exception {
        SpanType span = SpanType.WEEK;
        int periodIndex = 0;
        var view = StatisticsView.COURSE;
        var courseId = course.getId();
        for (GraphType graph : courseGraphs) {
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            parameters.add("view", "" + view);
            parameters.add("entityId", "" + courseId);
            Integer[] result = request.get("/api/management/statistics/data-for-content", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(7);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetChartDataForExercise() throws Exception {
        SpanType span = SpanType.WEEK;
        int periodIndex = 0;
        var view = StatisticsView.EXERCISE;
        var exerciseId = exercise.getId();
        for (GraphType graph : exerciseGraphs) {
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            parameters.add("view", "" + view);
            parameters.add("entityId", "" + exerciseId);
            Integer[] result = request.get("/api/management/statistics/data-for-content", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(7);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetCourseStatistics() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise firstTextExercise = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        TextExercise secondTextExercise = database.createIndividualTextExercise(course, pastTimestamp.minusDays(1), pastTimestamp.minusDays(1), pastTimestamp.minusDays(1));

        var firstTextExerciseId = firstTextExercise.getId();
        var secondTextExerciseId = secondTextExercise.getId();
        User student1 = userRepository.findOneByLogin("student1").orElseThrow();
        User student2 = userRepository.findOneByLogin("student2").orElseThrow();

        // Creating result for student1 and student2 for firstExercise
        database.createParticipationSubmissionAndResult(firstTextExerciseId, student1, 10.0, 0.0, 50, true);
        database.createParticipationSubmissionAndResult(firstTextExerciseId, student2, 10.0, 0.0, 100, true);

        // Creating result for student1 and student2 for secondExercise
        database.createParticipationSubmissionAndResult(secondTextExerciseId, student1, 10.0, 0.0, 0, true);
        database.createParticipationSubmissionAndResult(secondTextExerciseId, student2, 10.0, 0.0, 80, true);

        Long courseId = course.getId();
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("courseId", "" + courseId);
        CourseManagementStatisticsDTO result = request.get("/api/management/statistics/course-statistics", HttpStatus.OK, CourseManagementStatisticsDTO.class, parameters);

        assertThat(result.getAverageScoreOfCourse()).isEqualTo(57.5);
        assertThat(result.getAverageScoresOfExercises().size()).isEqualTo(2);

        var firstTextExerciseStatistics = result.getAverageScoresOfExercises().get(0);
        assertThat(firstTextExerciseStatistics.getAverageScore()).isEqualTo(75.0);
        assertThat(firstTextExerciseStatistics.getExerciseId()).isEqualTo(firstTextExerciseId);
        assertThat(firstTextExerciseStatistics.getExerciseName()).isEqualTo(firstTextExercise.getTitle());

        var secondTextExerciseStatistics = result.getAverageScoresOfExercises().get(1);
        assertThat(secondTextExerciseStatistics.getAverageScore()).isEqualTo(40.0);
        assertThat(secondTextExerciseStatistics.getExerciseId()).isEqualTo(secondTextExerciseId);
        assertThat(secondTextExerciseStatistics.getExerciseName()).isEqualTo(secondTextExercise.getTitle());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetExerciseStatistics() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise textExercise = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);

        var firstTextExerciseId = textExercise.getId();
        User student1 = userRepository.findOneByLogin("student1").orElseThrow();
        User student2 = userRepository.findOneByLogin("student2").orElseThrow();

        // Creating result for student1 and student2 for firstExercise
        database.createParticipationSubmissionAndResult(firstTextExerciseId, student1, 10.0, 0.0, 50, true);
        database.createParticipationSubmissionAndResult(firstTextExerciseId, student2, 10.0, 0.0, 100, true);

        Post post = new Post();
        post.setExercise(textExercise);
        post.setContent("Test Student Question 1");
        post.setVisibleForStudents(true);
        post.setCreationDate(ZonedDateTime.now().minusHours(2));
        post.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        postRepository.save(post);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now().minusHours(1));
        answerPost.setTutorApproved(true);
        answerPost.setPost(post);
        answerPostRepository.save(answerPost);

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("exerciseId", "" + firstTextExerciseId);
        ExerciseManagementStatisticsDTO result = request.get("/api/management/statistics/exercise-statistics", HttpStatus.OK, ExerciseManagementStatisticsDTO.class, parameters);

        assertThat(result.getAverageScoreOfExercise()).isEqualTo(75.0);
        assertThat(result.getMaxPointsOfExercise()).isEqualTo(10);
        assertThat(result.getNumberOfExerciseScores()).isEqualTo(2);
        assertThat(result.getNumberOfParticipations()).isEqualTo(2);
        assertThat(result.getNumberOfStudentsOrTeamsInCourse()).isEqualTo(12);
        assertThat(result.getNumberOfQuestions()).isEqualTo(1);
        assertThat(result.getNumberOfAnsweredQuestions()).isEqualTo(1);
        var expectedScoresResult = new int[10];
        Arrays.fill(expectedScoresResult, 0);
        // We have one assessment with 50% and one with 100%
        expectedScoresResult[5] = 1;
        expectedScoresResult[9] = 1;
        assertThat(result.getScoreDistribution()).isEqualTo(expectedScoresResult);
    }
}
