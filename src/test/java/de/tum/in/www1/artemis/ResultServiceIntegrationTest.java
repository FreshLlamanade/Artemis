package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ResultServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ExamRepository examRepository;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private ModelingExercise modelingExercise;

    private ModelingExercise examModelingExercise;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private StudentParticipation studentParticipation;

    @BeforeEach
    public void reset() {
        database.addUsers(10, 2, 0, 2);
        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise).findAny().orElseThrow();
        ProgrammingExercise programmingExerciseWithStaticCodeAnalysis = database.addProgrammingExerciseToCourse(course, true);
        // This is done to avoid proxy issues in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).get();
        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExerciseWithStaticCodeAnalysis, "student1");

        database.addCourseWithOneModelingExercise();
        modelingExercise = modelingExerciseRepository.findAll().get(0);
        modelingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        studentParticipation = database.createAndSaveParticipationForExercise(modelingExercise, "student2");

        Exam exam = database.addExamWithExerciseGroup(this.course, true);
        this.examModelingExercise = new ModelingExercise();
        this.examModelingExercise.setMaxPoints(100D);
        this.examModelingExercise.setExerciseGroup(exam.getExerciseGroups().get(0));
        this.modelingExerciseRepository.save(this.examModelingExercise);
        this.examRepository.save(exam);

        Result result = ModelFactory.generateResult(true, 200D).resultString("Good effort!").participation(programmingExerciseStudentParticipation);
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Good work here")).collect(Collectors.toList());
        result.setFeedbacks(feedbacks);
        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(ArgumentMatchers.any());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testRemoveCIDirectoriesFromPath() {
        // 1. Test that paths not containing the Constant.STUDENT_WORKING_DIRECTORY are not shortened
        String pathWithoutWorkingDir = "Path/Without/StudentWorkingDirectory/Constant";

        var resultNotification1 = ModelFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        for (var reports : resultNotification1.getBuild().getJobs().iterator().next().getStaticCodeAnalysisReports()) {
            for (var issue : reports.getIssues()) {
                issue.setFilePath(pathWithoutWorkingDir);
            }
        }
        var staticCodeAnalysisFeedback1 = feedbackRepository
                .createFeedbackFromStaticCodeAnalysisReports(resultNotification1.getBuild().getJobs().get(0).getStaticCodeAnalysisReports());

        for (var feedback : staticCodeAnalysisFeedback1) {
            JSONObject issueJSON = new JSONObject(feedback.getDetailText());
            assertThat(pathWithoutWorkingDir).isEqualTo(issueJSON.get("filePath"));
        }

        // 2. Test that null or empty paths default to FeedbackService.DEFAULT_FILEPATH
        var resultNotification2 = ModelFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        var reports2 = resultNotification2.getBuild().getJobs().iterator().next().getStaticCodeAnalysisReports();
        for (int i = 0; i < reports2.size(); i++) {
            var report = reports2.get(i);
            // Set null or empty String to test both
            if (i % 2 == 0) {
                for (var issue : report.getIssues()) {
                    issue.setFilePath("");
                }
            }
            else {
                for (var issue : report.getIssues()) {
                    issue.setFilePath(null);
                }
            }
        }
        final var staticCodeAnalysisFeedback2 = feedbackRepository
                .createFeedbackFromStaticCodeAnalysisReports(resultNotification2.getBuild().getJobs().get(0).getStaticCodeAnalysisReports());

        for (var feedback : staticCodeAnalysisFeedback2) {
            JSONObject issueJSON = new JSONObject(feedback.getDetailText());
            assertThat(FeedbackRepository.DEFAULT_FILEPATH).isEqualTo(issueJSON.get("filePath"));
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation() throws Exception {
        Result result = database.addResultToParticipation(null, null, programmingExerciseStudentParticipation);
        result = database.addSampleFeedbackToResults(result);

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsWithStaticCodeAnalysisFeedbackForAProgrammingExerciseStudentParticipation() throws Exception {
        Result result = database.addResultToParticipation(null, null, programmingExerciseStudentParticipation);
        result = database.addSampleStaticCodeAnalysisFeedbackToResults(result);

        List<Feedback> feedback = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedback).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnOnlyAutomaticFeedbackForAProgrammingExerciseStudentParticipationBeforeAssessDueDate() throws Exception {
        Result result = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), programmingExerciseStudentParticipation);
        Feedback feedback1 = new Feedback().detailText("automatic1").type(FeedbackType.AUTOMATIC);
        Feedback feedback2 = new Feedback().detailText("automatic2").type(FeedbackType.AUTOMATIC);
        Feedback feedback3 = new Feedback().detailText("manual1").type(FeedbackType.MANUAL);
        result = database.addFeedbackToResult(feedback1, result);
        result = database.addFeedbackToResult(feedback2, result);
        result = database.addFeedbackToResult(feedback3, result);

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks().stream().filter(f -> f.getType().equals(FeedbackType.AUTOMATIC)).collect(Collectors.toList()));
        assertThat(feedbacks.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void shouldReturnTheResultDetailsForAStudentParticipation() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(value = "student2", roles = "USER")
    public void shouldReturnTheResultDetailsForAStudentParticipationWithSensitiveInformationFiltered(boolean isAfterDueDate) throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        result = database.addVariousVisibilityFeedbackToResults(result);

        if (isAfterDueDate) {
            database.updateExerciseDueDate(studentParticipation.getExercise().getId(), ZonedDateTime.now().minusHours(10));
        }
        else {
            // Set programming exercise due date in future.
            database.updateExerciseDueDate(studentParticipation.getExercise().getId(), ZonedDateTime.now().plusHours(10));
        }

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks.stream().filter(Feedback::isInvisible)).hasSize(0);

        if (isAfterDueDate) {
            assertThat(feedbacks.size()).isEqualTo(4);
            assertThat(feedbacks.stream().filter(Feedback::isAfterDueDate)).hasSize(1);
        }
        else {
            assertThat(feedbacks.size()).isEqualTo(3);
            assertThat(feedbacks.stream().filter(Feedback::isAfterDueDate)).hasSize(0);
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void shouldReturnTheResultDetailsForAnInstructorWithoutSensitiveInformationFiltered(boolean isAfterDueDate) throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        result = database.addVariousVisibilityFeedbackToResults(result);

        if (isAfterDueDate) {
            database.updateExerciseDueDate(studentParticipation.getExercise().getId(), ZonedDateTime.now().minusHours(10));
        }
        else {
            // Set programming exercise due date in future.
            database.updateExerciseDueDate(studentParticipation.getExercise().getId(), ZonedDateTime.now().plusHours(10));
        }

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks.stream().filter(Feedback::isInvisible)).hasSize(1);
        assertThat(feedbacks.size()).isEqualTo(5);
        assertThat(feedbacks.stream().filter(Feedback::isAfterDueDate)).hasSize(1);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAStudentParticipation_studentForbidden() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);

        request.getList("/api/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation_studentForbidden() throws Exception {
        Result result = database.addResultToParticipation(null, null, solutionParticipation);
        result = database.addSampleFeedbackToResults(result);
        request.getList("/api/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnNotFoundForNonExistingResult() throws Exception {
        Result result = database.addResultToParticipation(null, null, solutionParticipation);
        database.addSampleFeedbackToResults(result);
        request.getList("/api/results/" + 11667 + "/details", HttpStatus.NOT_FOUND, Feedback.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void shouldNotFilterVisibilityNeverForInstructor() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        result = database.addVariousVisibilityFeedbackToResults(result);

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);
        assertThat(feedbacks.stream().filter(f -> f.getVisibility() == Visibility.NEVER)).hasSize(1);
        assertThat(feedbacks.stream().filter(f -> f.getVisibility() == Visibility.AFTER_DUE_DATE)).hasSize(1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("setResultRatedPermutations")
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void setProgrammingExerciseResultRated(boolean shouldBeRated, ZonedDateTime buildAndTestAfterDueDate, SubmissionType submissionType, ZonedDateTime dueDate) {

        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(submissionType).submitted(true)
                .submissionDate(ZonedDateTime.now());
        database.addProgrammingSubmission(programmingExercise, programmingSubmission, "student1");
        Result result = database.addResultToParticipation(programmingExerciseStudentParticipation, programmingSubmission);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestAfterDueDate);
        programmingExercise.setDueDate(dueDate);
        programmingExerciseRepository.save(programmingExercise);

        result.setRatedIfNotExceeded(programmingExercise.getDueDate(), programmingSubmission);
        assertThat(result.isRated() == shouldBeRated).isTrue();
    }

    private static Stream<Arguments> setResultRatedPermutations() {
        ZonedDateTime dateInFuture = ZonedDateTime.now().plusHours(1);
        ZonedDateTime dateInPast = ZonedDateTime.now().minusHours(1);
        return Stream.of(
                // The due date has not passed, normal student submission => rated result.
                Arguments.of(true, null, SubmissionType.MANUAL, dateInFuture),
                // The due date is not set, normal student submission => rated result.
                Arguments.of(true, null, SubmissionType.MANUAL, null),
                // The due date has passed, normal student submission => unrated result.
                Arguments.of(false, null, SubmissionType.MANUAL, dateInPast),
                // The result was generated by an instructor => rated result.
                Arguments.of(true, null, SubmissionType.INSTRUCTOR, dateInPast),
                // The result was generated by test update => rated result.
                Arguments.of(true, null, SubmissionType.TEST, dateInPast),
                // The build and test date has passed, the due date has passed, the result is generated by a test update => rated result.
                Arguments.of(true, dateInPast, SubmissionType.TEST, dateInPast),
                // The build and test date has passed, the due date has passed, the result is generated by an instructor => rated result.
                Arguments.of(true, dateInPast, SubmissionType.INSTRUCTOR, dateInPast),
                // The build and test date has not passed, due date has not passed, normal student submission => rated result.
                Arguments.of(true, dateInFuture, SubmissionType.MANUAL, dateInFuture),
                // The build and test date has not passed, due date has passed, normal student submission => unrated result.
                Arguments.of(false, dateInFuture, SubmissionType.MANUAL, dateInPast));
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForProgrammingExercise() throws Exception {
        var now = ZonedDateTime.now();

        for (int i = 1; i <= 10; i++) {
            ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
            programmingSubmission.submitted(true);
            programmingSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(programmingExercise, programmingSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(programmingSubmission, AssessmentType.AUTOMATIC, null, 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(programmingSubmission, AssessmentType.AUTOMATIC, null, 20D, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + programmingExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForQuizExercise() throws Exception {
        var now = ZonedDateTime.now();

        QuizExercise quizExercise = database.createQuiz(course, now.minusMinutes(5), now.minusMinutes(2));
        quizExerciseRepository.save(quizExercise);

        for (int i = 1; i <= 10; i++) {
            QuizSubmission quizSubmission = new QuizSubmission();
            quizSubmission.setScoreInPoints(2.0);
            quizSubmission.submitted(true);
            quizSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(quizExercise, quizSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 20D, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + quizExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForModelingExercise() throws Exception {
        var now = ZonedDateTime.now();
        for (int i = 1; i <= 10; i++) {
            ModelingSubmission modelingSubmission = new ModelingSubmission();
            modelingSubmission.model("Text");
            modelingSubmission.submitted(true);
            modelingSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(modelingExercise, modelingSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(modelingSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"), 20D, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + modelingExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForTextExercise() throws Exception {
        var now = ZonedDateTime.now();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course);
        course.addExercises(textExercise);
        textExerciseRepository.save(textExercise);

        for (int i = 1; i <= 10; i++) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.text("Text");
            textSubmission.submitted(true);
            textSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(textExercise, textSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"), 20D, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + textExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForFileUploadExercise() throws Exception {
        var now = ZonedDateTime.now();
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), "pdf", course);
        course.addExercises(fileUploadExercise);
        fileUploadExerciseRepository.save(fileUploadExercise);

        for (int i = 1; i <= 10; i++) {
            FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
            fileUploadSubmission.submitted(true);
            fileUploadSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(fileUploadExercise, fileUploadSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(fileUploadSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(fileUploadSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 20D, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getResult() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        Result returnedResult = request.get("/api/results/" + result.getId(), HttpStatus.OK, Result.class);
        assertThat(returnedResult).isNotNull();
        assertThat(returnedResult).isEqualTo(result);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getResult_asStudent() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        request.get("/api/results/" + result.getId(), HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForExamExercise() throws Exception {
        var now = ZonedDateTime.now();
        for (int i = 1; i <= 5; i++) {
            ModelingSubmission modelingSubmission = new ModelingSubmission();
            modelingSubmission.model("Testingsubmission");
            modelingSubmission.submitted(true);
            modelingSubmission.submissionDate(now.minusHours(2));
            database.addSubmission(this.examModelingExercise, modelingSubmission, "student" + i);
            database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 12D, true);
        }

        // empty participation with submission
        StudentParticipation participation = new StudentParticipation();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(null);
        participation.setExercise(this.examModelingExercise);
        studentParticipationRepository.save(participation);
        ModelingSubmission modelingSubmission = new ModelingSubmission();
        modelingSubmission.model("Text");
        modelingSubmission.submitted(true);
        modelingSubmission.submissionDate(now.minusHours(3));
        participation.addSubmission(modelingSubmission);
        modelingSubmission.setParticipation(participation);
        submissionRepository.save(modelingSubmission);
        studentParticipationRepository.save(participation);

        List<Result> results = request.getList("/api/exercises/" + this.examModelingExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getLatestResultWithFeedbacks() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result.setCompletionDate(ZonedDateTime.now().minusHours(10));
        Result latestResult = database.addResultToParticipation(null, null, studentParticipation);
        latestResult.setCompletionDate(ZonedDateTime.now());
        database.addSampleFeedbackToResults(result);
        latestResult = database.addSampleFeedbackToResults(latestResult);
        Result returnedResult = request.get("/api/participations/" + studentParticipation.getId() + "/latest-result", HttpStatus.OK, Result.class);
        assertThat(returnedResult).isNotNull();
        assertThat(returnedResult).isEqualTo(latestResult);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getLatestResultWithFeedbacks_asStudent() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        database.addSampleFeedbackToResults(result);
        request.get("/api/participations/" + studentParticipation.getId() + "/latest-result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void deleteResult() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        request.delete("/api/results/" + result.getId(), HttpStatus.OK);
        assertThat(resultRepository.existsById(result.getId())).isFalse();
        request.delete("api/results/" + result.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void deleteResultStudent() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        request.delete("/api/results/" + result.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getResultForSubmission() throws Exception {
        var now = ZonedDateTime.now();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course);
        course.addExercises(textExercise);
        textExerciseRepository.save(textExercise);
        TextSubmission textSubmission = new TextSubmission();
        textSubmission = (TextSubmission) database.addSubmission(textExercise, textSubmission, "student1");
        textSubmission = (TextSubmission) database.addResultToSubmission(textSubmission, null);
        Result returnedResult = request.get("/api/results/submission/" + textSubmission.getId(), HttpStatus.OK, Result.class);
        assertThat(returnedResult).isEqualTo(textSubmission.getLatestResult());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createExampleResult() throws Exception {
        var modelingSubmission = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        var exampleSubmission = ModelFactory.generateExampleSubmission(modelingSubmission, modelingExercise, false);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);
        modelingSubmission.setExampleSubmission(true);
        submissionRepository.save(modelingSubmission);
        request.postWithResponseBody("/api/submissions/" + modelingSubmission.getId() + "/example-result", exampleSubmission, Result.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission() throws Exception {
        Result result = new Result().rated(false);
        var createdResult = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class,
                HttpStatus.CREATED);
        assertThat(createdResult).isNotNull();
        assertThat(createdResult.isRated()).isFalse();
        // TODO: we should assert that the result has been created with all corresponding objects in the database
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_programmingExercise() throws Exception {
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.enableMockingOfRequests(true);
        var studentLogin = "student1";
        User user = userRepository.findOneByLogin(studentLogin).orElseThrow();
        mockConnectorRequestsForStartParticipation(programmingExercise, user.getParticipantIdentifier(), Set.of(user), true, HttpStatus.CREATED);
        final var repositorySlug = (programmingExercise.getProjectKey() + "-" + studentLogin).toLowerCase();
        bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(repositorySlug, programmingExercise.getProjectKey(), Set.of(user));
        Result result = new Result().rated(false);
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(5));
        programmingExerciseRepository.save(programmingExercise);
        var createdResult = request.postWithResponseBody(externalResultPath(programmingExercise.getId(), studentLogin), result, Result.class, HttpStatus.CREATED);
        assertThat(createdResult).isNotNull();
        assertThat(createdResult.isRated()).isFalse();
        // TODO: we should assert that the result has been created with all corresponding objects in the database
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_quizExercise() throws Exception {
        var now = ZonedDateTime.now();
        var quizExercise = ModelFactory.generateQuizExercise(now.minusDays(1), now.minusHours(2), course);
        course.addExercises(quizExercise);
        quizExerciseRepository.save(quizExercise);
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(quizExercise.getId(), "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_studentNotInTheCourse() throws Exception {
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), "student11"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmissionExam() throws Exception {
        Result result = new Result().rated(false);
        request.postWithResponseBody("/api/exercises/" + this.examModelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    private String externalResultPath(long exerciseId, String studentLogin) {
        return "/api/exercises/" + exerciseId + "/external-submission-results?studentLogin=" + studentLogin;
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_dueDateNotPassed() throws Exception {
        modelingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_resultExists() throws Exception {
        var now = ZonedDateTime.now();
        var modelingExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course);
        course.addExercises(modelingExercise);
        modelingExerciseRepository.save(modelingExercise);
        var participation = database.createAndSaveParticipationForExercise(modelingExercise, "student1");
        var result = database.addResultToParticipation(null, null, participation);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetAssessmentCountByCorrectionRound() {
        // exercise
        TextExercise textExercise = new TextExercise();
        textExerciseRepository.save(textExercise);

        // participation
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setExercise(textExercise);
        studentParticipationRepository.save(studentParticipation);

        // submission
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setParticipation(studentParticipation);
        textSubmission.setSubmitted(true);
        textSubmission.setText("abc");
        textSubmission = submissionRepository.save(textSubmission);

        // result 1
        var result1 = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), studentParticipation, "text result string 1", "instructor1", new ArrayList<>());
        result1.setRated(true);
        result1 = database.addFeedbackToResults(result1);
        result1.setSubmission(textSubmission);

        // result 2
        var result2 = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), studentParticipation, "text result string 2", "tutor1", new ArrayList<>());
        result2.setRated(true);
        result2 = database.addFeedbackToResults(result2);
        result2.setSubmission(textSubmission);

        textSubmission.addResult(result1);
        textSubmission = submissionRepository.save(textSubmission);

        textSubmission.addResult(result2);
        submissionRepository.save(textSubmission);

        var assessments = resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(textExercise, 2);
        assertThat(assessments[0].inTime()).isEqualTo(1);    // correction round 1
        assertThat(assessments[1].inTime()).isEqualTo(1);    // correction round 2
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetAssessmentCountByCorrectionRoundForProgrammingExercise() {
        // exercise
        Course course = database.createCourse();
        ProgrammingExercise programmingExercise = database.addProgrammingExerciseToCourse(course, false);
        programmingExercise.setDueDate(null);
        programmingExerciseRepository.save(programmingExercise);

        // participation
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
        programmingExerciseStudentParticipation.setExercise(programmingExercise);
        programmingExerciseStudentParticipationRepository.save(programmingExerciseStudentParticipation);

        // submission
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setParticipation(programmingExerciseStudentParticipation);
        programmingSubmission.setSubmitted(true);
        programmingSubmission.setBuildArtifact(true);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // result 1
        Result r1 = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), programmingExerciseStudentParticipation, "text result string 1", "instructor1",
                new ArrayList<>());
        r1.setRated(true);
        r1 = database.addFeedbackToResults(r1);
        r1.setSubmission(programmingSubmission);

        // result 2
        Result r2 = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), programmingExerciseStudentParticipation, "text result string 2", "tutor1",
                new ArrayList<>());
        r2.setRated(true);
        r2 = database.addFeedbackToResults(r2);
        r2.setSubmission(programmingSubmission);

        programmingSubmission.addResult(r1);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        programmingSubmission.addResult(r2);
        submissionRepository.save(programmingSubmission);

        var assessments = resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(programmingExercise, 2);
        assertThat(assessments[0].inTime()).isEqualTo(1);    // correction round 1
        assertThat(assessments[1].inTime()).isEqualTo(1);    // correction round 2
    }
}
