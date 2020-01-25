package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingInstruction;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.GradingInstructionService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing Structured Grading Instructions.
 */
@RestController
@RequestMapping({ GradingInstructionResource.Endpoints.ROOT })
public class GradingInstructionResource {

    private final Logger log = LoggerFactory.getLogger(GradingInstructionResource.class);

    private static final String ENTITY_NAME = "gradingInstruction";

    @Value("${jhipster.clientApp.name}")
    private String APPLICATION_NAME;

    private final GradingInstructionService gradingInstructionService;

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authCheckService;

    public GradingInstructionResource(GradingInstructionService gradingInstructionService, ExerciseService exerciseService, AuthorizationCheckService authCheckService) {
        this.gradingInstructionService = gradingInstructionService;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /exercises/:exerciseId/grading-instructions : get the "id" exercise.
     *
     * @param exerciseId the id of the exercise to retrieve its grading instructions
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping(Endpoints.GRADING_INSTRUCTIONS_OF_EXERCISE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<GradingInstruction>> getGradingInstructionsByExerciseId(@PathVariable long exerciseId) {
        log.debug("REST request to get Exercise : {}", exerciseId);

        Exercise exercise = exerciseService.findOne(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        List<GradingInstruction> gradingInstructions = gradingInstructionService.findAllForExercise(exercise);
        return ResponseEntity.ok(gradingInstructions);
    }

    /**
     * POST /grading-instructions : Create a new gradingInstruction.
     *
     * @param gradingInstruction the gradingInstruction to create
     * @return the ResponseEntity with status 201 (Created) and with body the new gradingInstruction, or with status 400 (Bad Request) if the gradingInstruction has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(Endpoints.GRADING_INSTRUCTIONS)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingInstruction> createGradingInstruction(@RequestBody GradingInstruction gradingInstruction) throws URISyntaxException {
        log.debug("REST request to save GradingInstruction : {}", gradingInstruction);
        if (gradingInstruction.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(APPLICATION_NAME, true, ENTITY_NAME, "idexists", "A new gradingInstruction cannot already have an ID")).body(null);
        }

        // fetch exercise from database to make sure client didn't change groups
        Exercise exercise = exerciseService.findOne(gradingInstruction.getExercise().getId());
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        gradingInstruction = gradingInstructionService.save(gradingInstruction);
        return ResponseEntity.created(new URI(Endpoints.ROOT + Endpoints.GRADING_INSTRUCTIONS + "/" + gradingInstruction.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(APPLICATION_NAME, true, ENTITY_NAME, gradingInstruction.getId().toString())).body(gradingInstruction);

    }

    /**
     * PUT /grading-instructions : Updates an existing gradingInstruction.
     *
     * @param gradingInstruction the gradingInstruction to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated gradingInstruction, or with status 400 (Bad Request) if the gradingInstruction is not valid, or with status 500
     * (Internal Server Error) if the gradingInstruction couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping(Endpoints.GRADING_INSTRUCTIONS)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingInstruction> updateGradingInstruction(@RequestBody GradingInstruction gradingInstruction) throws URISyntaxException {
        log.debug("REST request to update GradingInstruction : {}", gradingInstruction);
        if (gradingInstruction.getId() == null) {
            return createGradingInstruction(gradingInstruction);
        }

        // fetch exercise from database to make sure client didn't change groups
        Exercise exercise = exerciseService.findOne(gradingInstruction.getExercise().getId());
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        gradingInstruction = gradingInstructionService.save(gradingInstruction);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(APPLICATION_NAME, true, ENTITY_NAME, gradingInstruction.getId().toString())).body(gradingInstruction);
    }

    /**
     * DELETE /grading-instructions/:gradingInstructionId : delete the "id" gradingInstruction.
     *
     * @param gradingInstructionId the id of the gradingInstruction to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping(Endpoints.GRADING_INSTRUCTION)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradingInstruction(@PathVariable long gradingInstructionId) {
        log.debug("REST request to delete GradingInstruction : {}", gradingInstructionId);

        GradingInstruction gradingInstruction = gradingInstructionService.findOne(gradingInstructionId);
        Exercise exercise = exerciseService.findOne(gradingInstruction.getExercise().getId());
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        gradingInstructionService.delete(gradingInstruction);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(APPLICATION_NAME, true, ENTITY_NAME, gradingInstructionId + "")).build();
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String GRADING_INSTRUCTIONS = "/grading-instructions";

        public static final String GRADING_INSTRUCTION = GRADING_INSTRUCTIONS + "/{gradingInstructionId}";

        public static final String EXERCISES = "/exercises";

        public static final String EXERCISE = EXERCISES + "/{exerciseId}";

        public static final String GRADING_INSTRUCTIONS_OF_EXERCISE = EXERCISE + GRADING_INSTRUCTIONS;

        private Endpoints() {
        }

    }
}
