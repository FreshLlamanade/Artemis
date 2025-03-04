package de.tum.in.www1.artemis.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class GradingScaleService {

    private final GradingScaleRepository gradingScaleRepository;

    private final GradeStepRepository gradeStepRepository;

    public GradingScaleService(GradingScaleRepository gradingScaleRepository, GradeStepRepository gradeStepRepository) {
        this.gradeStepRepository = gradeStepRepository;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    /**
     * Saves a grading scale to the database if it is valid
     * - grading scale can't have both course and exam set
     * - other checks performed in {@link GradingScaleService#checkGradeStepValidity(Set)}
     *
     * @param gradingScale the grading scale to be saved
     * @return the saved grading scale
     */
    public GradingScale saveGradingScale(GradingScale gradingScale) {
        if (gradingScale.getCourse() != null && gradingScale.getExam() != null) {
            throw new BadRequestAlertException("Grading scales can't belong both to a course and an exam", "gradingScale", "gradingScaleBelongsToCourseAndExam");
        }
        Set<GradeStep> gradeSteps = gradingScale.getGradeSteps();
        checkGradeStepValidity(gradeSteps);
        for (GradeStep gradeStep : gradeSteps) {
            gradeStep.setGradingScale(gradingScale);
        }
        gradingScale.setGradeSteps(gradeSteps);
        return gradingScaleRepository.save(gradingScale);
    }

    /**
     * Checks the validity of a grade step set and throws an exception if one of the following conditions is not fulfilled
     * - all individuals grade steps should be in a valid format
     * - the grade steps set should form a valid and congruent grading scale
     *
     * @param gradeSteps the grade steps to be checked
     */
    private void checkGradeStepValidity(Set<GradeStep> gradeSteps) {
        if (gradeSteps != null && !gradeSteps.isEmpty()) {
            if (!gradeSteps.stream().allMatch(GradeStep::checkValidity)) {
                throw new BadRequestAlertException("Not all grade steps are following the correct format.", "gradeStep", "invalidGradeStepFormat");
            }
            else if (!gradeStepSetMapsToValidGradingScale(gradeSteps)) {
                throw new BadRequestAlertException("Grade step set can't match to a valid grading scale.", "gradeStep", "invalidGradeStepAdjacency");
            }
        }
        else {
            throw new BadRequestAlertException("Grade steps can't be empty", "gradeStep", "emptyGradeSteps");
        }
    }

    /**
     * Checks if the grade steps map to a valid grading scale
     * - the grade names should all be unique for the grading scale
     * - when ordered, all steps should fulfill valid adjacency
     * - the first and the last element should fulfill the boundary conditions (start with 0% and end with 100%)
     *
     * @param gradeSteps the grade steps to be checked
     * @return true if the grade steps map to a valid grading scale and false otherwise
     */
    private boolean gradeStepSetMapsToValidGradingScale(Set<GradeStep> gradeSteps) {
        // all grade steps should have distinct names
        if (gradeSteps.stream().map(GradeStep::getGradeName).distinct().count() != gradeSteps.size()) {
            return false;
        }
        // sort by lower bound percentage
        List<GradeStep> sortedGradeSteps = gradeSteps.stream().sorted(Comparator.comparingDouble(GradeStep::getLowerBoundPercentage)).collect(Collectors.toList());
        // check if all pairs of the sorted grade steps have valid adjacency
        boolean validAdjacency = IntStream.range(0, sortedGradeSteps.size() - 1).allMatch(i -> GradeStep.checkValidAdjacency(sortedGradeSteps.get(i), sortedGradeSteps.get(i + 1)));
        // first step should start from and include 0
        boolean validFirstElement = sortedGradeSteps.get(0).isLowerBoundInclusive() && sortedGradeSteps.get(0).getLowerBoundPercentage() == 0;
        // last step should end with and include 100
        boolean validLastElement = sortedGradeSteps.get(sortedGradeSteps.size() - 1).isUpperBoundInclusive()
                && sortedGradeSteps.get(sortedGradeSteps.size() - 1).getUpperBoundPercentage() == 100;
        return validAdjacency && validFirstElement && validLastElement;
    }
}
