package de.tum.in.www1.artemis.service.compass;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ModelCluster;
import de.tum.in.www1.artemis.domain.modeling.ModelElement;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.controller.FeedbackSelector;
import de.tum.in.www1.artemis.service.compass.controller.ModelClusterFactory;
import de.tum.in.www1.artemis.service.compass.statistics.CompassStatistics;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

@Service
public class CompassService {

    private final Logger log = LoggerFactory.getLogger(CompassService.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ModelElementRepository modelElementRepository;

    private final ModelClusterRepository modelClusterRepository;

    private final FeedbackRepository feedbackRepository;

    public CompassService(ModelingExerciseRepository modelingExerciseRepository, ModelingSubmissionRepository modelingSubmissionRepository,
            ModelElementRepository modelElementRepository, ModelClusterRepository modelClusterRepository, FeedbackRepository feedbackRepository) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.modelClusterRepository = modelClusterRepository;
        this.modelElementRepository = modelElementRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Builds and saves the clusters
     * Does not build clusters if there is already a cluster for given exercise
     *
     * @param modelingExercise the exercise to build clusters for
     */
    public void build(ModelingExercise modelingExercise) {
        long start = System.nanoTime();

        List<ModelCluster> currentClusters = modelClusterRepository.findAllByExerciseIdWithEagerElements(modelingExercise.getId());
        if (!currentClusters.isEmpty()) {
            log.info("Clusters have already been built. First delete existing clusters and then rebuild them!");
            // Do not build submissions if this process has already been done before
            return;
        }
        List<ModelingSubmission> submissions = modelingSubmissionRepository.findSubmittedByExerciseIdWithEagerResultsAndFeedback(modelingExercise.getId());
        log.info("ModelCluster: start building clusters of {} submissions for modeling exercise {}", submissions.size(), modelingExercise.getId());

        ModelClusterFactory clusterFactory = new ModelClusterFactory();
        List<ModelCluster> modelClusters = clusterFactory.buildClusters(submissions, modelingExercise);
        log.info("ModelClusterTimelog: building clusters of {} submissions for modeling exercise {} done in {}", submissions.size(), modelingExercise.getId(),
                TimeLogUtil.formatDurationFrom(start));
        modelClusterRepository.saveAll(modelClusters);
        modelElementRepository.saveAll(modelClusters.stream().flatMap(modelCluster -> modelCluster.getModelElements().stream()).collect(Collectors.toList()));
        log.info("ModelClusterTimelog: building and saving clusters of {} submissions for exercise {} done in {}", submissions.size(), modelingExercise.getId(),
                TimeLogUtil.formatDurationFrom(start));

    }

    /**
     * Selects the feedback suggestion for each element in submission and creates a result from them
     * Returns null if no feedback can be selected or the submission has already a manual feedback
     *
     * @param modelingSubmission the submission to select feedbacks for
     * @param modelingExercise the modeling exercise to which the submission belongs
     * @return the semi automatic result that has the feedbacks inside
     */
    public Result getSuggestionResult(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        Result result = getAutomaticResultForSubmission(modelingSubmission);
        if (result != null) {
            List<Feedback> feedbacksForSuggestion = new ArrayList<>();
            ModelClusterFactory clusterBuilder = new ModelClusterFactory();
            List<UMLElement> elements = clusterBuilder.getModelElements(modelingSubmission);
            // TODO: the following for loop has too many database calls and will be very inefficient, because those are done for each model element and a model can easily have 100
            // model elements
            // we should instead fetch all elements with only 3 database calls
            // 1x modelElementRepository.findAllModelElementsByIds
            // 1x modelClusterRepository.findAllClustersWithEagerElementsByIds
            // 1x feedbackRepository.findByReferenceInAndResult_Submission_Participation_Exercise with all references of all model elements.
            // The for loop can then search in the retrieved Lists without additional database queries
            List<ModelElement> modelElements = modelElementRepository.findByModelElementIdIn(elements.stream().map(UMLElement::getJSONElementID).collect(Collectors.toList()));
            List<Long> clusterIds = modelElements.stream().map(ModelElement::getCluster).map(ModelCluster::getId).collect(Collectors.toList());
            List<ModelCluster> modelClusters = modelClusterRepository.findAllByIdInWithEagerElements(clusterIds);
            List<String> references = modelClusters.stream().flatMap(modelCluster -> modelCluster.getModelElements().stream())
                    .map(modelElement1 -> modelElement1.getModelElementType() + ":" + modelElement1.getModelElementId()).collect(Collectors.toList());
            List<Feedback> feedbacks = feedbackRepository.findByReferenceInAndResult_Submission_Participation_Exercise(references, modelingExercise);
            for (ModelElement modelElement : modelElements) {
                if (modelElement != null) {
                    ModelCluster cluster = modelClusters.get(modelClusters.indexOf(modelElement.getCluster()));
                    Set<ModelElement> similarElements = cluster.getModelElements();
                    List<String> similarReferences = similarElements.stream().map(modelElement1 -> modelElement1.getModelElementType() + ":" + modelElement1.getModelElementId())
                            .collect(Collectors.toList());
                    List<Feedback> similarFeedbacks = feedbacks.stream().filter(feedback -> similarReferences.contains(feedback.getReference())).collect(Collectors.toList());
                    Feedback suggestedFeedback = FeedbackSelector.selectFeedback(modelElement, similarFeedbacks, result);
                    if (suggestedFeedback != null) {
                        feedbacksForSuggestion.add(suggestedFeedback);
                    }
                }
            }
            if (feedbacksForSuggestion.isEmpty()) {
                return null;
            }
            result.getFeedbacks().clear(); // Note, that a result is always initialized with an empty list -> no NPE here
            result.getFeedbacks().addAll(feedbacksForSuggestion);
            result.setHasFeedback(false);
            result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        }
        return result;
    }

    /**
     * Get the result of the given modeling submission. If the given submission already contains a manual result, this result is returned. Otherwise, it tries to load and return
     * the result for the submission from the hash map containing all automatic results. If no result could be found in the hash map, a new result is created for the given
     * submission.
     *
     * @param modelingSubmission the submission for which the result should be obtained
     * @return the result of the given submission either obtained from the submission or the semi automatic result map, or a newly created one if it does not exist already
     */
    private Result getAutomaticResultForSubmission(ModelingSubmission modelingSubmission) {
        Result result = modelingSubmission.getLatestResult();

        if (result == null || !AssessmentType.MANUAL.equals(result.getAssessmentType())) {
            if (result == null) {
                StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
                result = new Result().submission(modelingSubmission).participation(studentParticipation);
            }
            return result;
        }

        return null;
    }

    /**
     * Indicates if the given diagram type is supported by Compass. At the moment Compass only support class diagrams.
     *
     * @param modelingExercise the modelingExercise that should be checked if automatic assessment is supported
     * @return true if the given diagram type is supported by Compass, false otherwise
     */
    public boolean isSupported(ModelingExercise modelingExercise) {
        // only use compass for course exercises, in exam exercises the additional delay is too much so it is currently deactivated
        // TODO: we should support compass also for the exam mode
        if (modelingExercise.isExamExercise()) {
            return false;
        }

        // In case the instructor specifies in the UI whether the semi-automatic assessment is possible or not.
        // NOTE: Currently, this is only possible for for exercises with class or activity diagrams
        DiagramType diagramType = modelingExercise.getDiagramType();
        if (modelingExercise.getAssessmentType() != null) {
            return (modelingExercise.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC)
                    && (diagramType == DiagramType.ClassDiagram || diagramType == DiagramType.ActivityDiagram);
        }
        // if the assessment mode is not specified (e.g. for legacy exercises), team exercises are not supported
        if (modelingExercise.isTeamMode()) {
            return false;
        }
        return diagramType == DiagramType.ClassDiagram || diagramType == DiagramType.ActivityDiagram;
    }

    /**
     * Indicates if the diagram type of the given exercise is supported by Compass. At the moment Compass only support class diagrams.
     *
     * @param exerciseId the id of the exercise that should be checked
     * @return true if the diagram type of the given exercise is supported by Compass, false otherwise
     */
    private boolean isSupported(long exerciseId) {
        Optional<ModelingExercise> optionalModelingExercise = modelingExerciseRepository.findById(exerciseId);
        if (!optionalModelingExercise.isPresent()) {
            log.error("Exercise with ID {} could not be found", exerciseId);
            return false;
        }

        return isSupported(optionalModelingExercise.get());
    }

    /**
     * format: uniqueElements [{id} name apollonId conflicts] numberModels numberConflicts totalConfidence totalCoverage models [{id} confidence coverage conflicts]
     *
     * @param exerciseId The ID of the exercise for which to collect statistics
     * @return statistics about the UML model
     */
    public JsonObject getStatistics(long exerciseId) {
        if (!isSupported(exerciseId)) {
            return new JsonObject();
        }
        return new CompassStatistics().getStatistics();
    }

    /**
     * Print statistics of the modeling exercise with the given id for internal analysis.
     *
     * @param exerciseId the id of the modeling exercise for which the statistic should be printed
     */
    public void printStatistic(Long exerciseId) {
        new CompassStatistics().getStatistics();
    }
}
