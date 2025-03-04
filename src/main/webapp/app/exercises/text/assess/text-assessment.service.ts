import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Feedback } from 'app/entities/feedback.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextBlock } from 'app/entities/text-block.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { cloneDeep } from 'lodash';
import { TextSubmission } from 'app/entities/text-submission.model';
import { FeedbackConflict } from 'app/entities/feedback-conflict';
import { getLatestSubmissionResult, getSubmissionResultByCorrectionRound, getSubmissionResultById, setLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { Participation } from 'app/entities/participation/participation.model';

type EntityResponseType = HttpResponse<Result>;
type TextAssessmentDTO = { feedbacks: Feedback[]; textBlocks: TextBlock[] };

@Injectable({
    providedIn: 'root',
})
export class TextAssessmentService {
    private readonly resourceUrl = SERVER_API_URL + 'api/text-assessments';

    constructor(private http: HttpClient) {}

    /**
     * Saves the passed feedback items of the assessment.
     * @param exerciseId id of the exercise the assessed submission was made to of type {number}
     * @param resultId id of the corresponding result of type {number}
     * @param feedbacks list of feedback made during assessment of type {Feedback[]}
     * @param textBlocks list of text blocks of type {TextBlock[]}
     */
    public save(exerciseId: number, resultId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}`, body, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => TextAssessmentService.convertResponse(res)));
    }

    /**
     * Submits the passed feedback items of the assessment.
     * @param exerciseId id of the exercise the assessed submission was made to of type {number}
     * @param resultId id of the corresponding result of type {number}
     * @param feedbacks list of feedback made during assessment of type {Feedback[]}
     * @param textBlocks list of text blocks of type {TextBlock[]}
     */
    public submit(exerciseId: number, resultId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}/submit`, body, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => TextAssessmentService.convertResponse(res)));
    }

    /**
     * Updates an assessment after a complaint.
     * @param feedbacks list of feedback made during assessment of type {Feedback[]}
     * @param textBlocks list of text blocks of type {TextBlock[]}
     * @param complaintResponse response on the complaint of type {ComplaintResponse}
     * @param submissionId id of corresponding submission of type {number}
     */
    public updateAssessmentAfterComplaint(
        feedbacks: Feedback[],
        textBlocks: TextBlock[],
        complaintResponse: ComplaintResponse,
        submissionId: number,
    ): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/text-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            textBlocks,
            complaintResponse,
        };
        return this.http.put<Result>(url, assessmentUpdate, { observe: 'response' }).pipe(map((res: EntityResponseType) => TextAssessmentService.convertResponse(res)));
    }

    saveExampleAssessment(exampleSubmissionId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/text-submissions/${exampleSubmissionId}/example-assessment`;
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http.put<Result>(url, body, { observe: 'response' }).pipe(map((res: EntityResponseType) => TextAssessmentService.convertResponse(res)));
    }

    /**
     * Cancels an assessment.
     * @param exerciseId id of the exercise the assessed submission was made to of type {number}
     * @param submissionId id of corresponding submission of type {number}
     */
    public cancelAssessment(exerciseId: number, submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/cancel-assessment`, null);
    }

    /**
     * Deletes an assessment.
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(submissionId: number, resultId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/text-submissions/${submissionId}/delete/${resultId}`);
    }

    /**
     * Get all feedback items for a submission.
     * @param submissionId id of the submission for which the feedback items should be retrieved of type {number}
     * @param correctionRound
     * @param resultId instructors can results by id
     */
    public getFeedbackDataForExerciseSubmission(submissionId: number, correctionRound = 0, resultId?: number): Observable<StudentParticipation> {
        let params = new HttpParams();
        if (resultId && resultId > 0) {
            // in case resultId is set, we do not need the correction round
            params = params.set('resultId', resultId!.toString());
        } else {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http.get<StudentParticipation>(`${this.resourceUrl}/submission/${submissionId}`, { observe: 'response', params }).pipe(
            // Wire up Result and Submission
            tap((response) => {
                const participation = response.body!;
                const submission = participation.submissions![0];
                let result;
                if (resultId) {
                    result = getSubmissionResultById(submission, resultId);
                } else {
                    result = getSubmissionResultByCorrectionRound(submission, correctionRound)!;
                }
                TextAssessmentService.reconnectResultsParticipation(participation, submission, result!);
                (submission as TextSubmission).atheneTextAssessmentTrackingToken = response.headers.get('x-athene-tracking-authorization') || undefined;
            }),
            map((response) => response.body!),
        );
    }

    /**
     * Gets an example result for defined exercise and submission.
     * @param exerciseId id of the exercise for which the example result should be retrieved of type {number}
     * @param submissionId id of the submission for which the example result should be retrieved of type {number}
     */
    public getExampleResult(exerciseId: number, submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/example-result`);
    }

    public deleteExampleFeedback(exampleSubmissionId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/text-submissions/${exampleSubmissionId}/example-assessment/feedback`);
    }

    /**
     * Gets an array of text submissions that contains conflicting feedback with the given feedback id.
     *
     * @param submissionId id of the submission feedback belongs to of type {number}
     * @param feedbackId id of the feedback to search for conflicts of type {number}
     */
    public getConflictingTextSubmissions(submissionId: number, feedbackId: number): Observable<TextSubmission[]> {
        return this.http.get<TextSubmission[]>(`${this.resourceUrl}/submission/${submissionId}/feedback/${feedbackId}/feedback-conflicts`);
    }

    /**
     * Set feedback conflict as solved. (Tutor decides it is not a conflict)
     *
     * @param exerciseId id of the exercise feedback conflict belongs to
     * @param feedbackConflictId id of the feedback conflict to be solved
     */
    public solveFeedbackConflict(exerciseId: number, feedbackConflictId: number): Observable<FeedbackConflict> {
        return this.http.get<FeedbackConflict>(`${this.resourceUrl}/exercise/${exerciseId}/feedbackConflict/${feedbackConflictId}/solve-feedback-conflict`);
    }

    private static prepareFeedbacksAndTextblocksForRequest(feedbacks: Feedback[], textBlocks: TextBlock[]): TextAssessmentDTO {
        feedbacks = feedbacks.map((feedback) => {
            feedback = Object.assign({}, feedback);
            delete feedback.result;
            delete feedback.conflictingTextAssessments;
            return feedback;
        });
        textBlocks = textBlocks.map((textBlock) => {
            textBlock = Object.assign({}, textBlock);
            textBlock.submission = undefined;
            return textBlock;
        });

        return { feedbacks, textBlocks };
    }

    private static convertResponse(res: EntityResponseType): EntityResponseType {
        const result = TextAssessmentService.convertItemFromServer(res.body!);

        if (result.completionDate) {
            result.completionDate = moment(result.completionDate);
        }
        if (result.submission && result.submission.submissionDate) {
            result.submission.submissionDate = moment(result.submission.submissionDate);
        }
        if (result.participation && result.participation.initializationDate) {
            result.participation.initializationDate = moment(result.participation.initializationDate);
        }

        return res.clone({ body: result });
    }

    private static convertItemFromServer(result: Result): Result {
        return Object.assign({}, result);
    }

    /**
     * Convert Feedback elements to use single array of FeedbackConflicts in the Feedback class.
     * It is stored with two references on the server side.
     *
     * @param feedbacks list of Feedback elements to convert.
     */
    private static convertFeedbackConflictsFromServer(feedbacks: Feedback[]): void {
        feedbacks.forEach((feedback) => {
            feedback.conflictingTextAssessments = [...(feedback['firstConflicts'] || []), ...(feedback['secondConflicts'] || [])];
            delete feedback['firstConflicts'];
            delete feedback['secondConflicts'];
            feedback.conflictingTextAssessments.forEach((textAssessmentConflict) => {
                textAssessmentConflict.conflictingFeedbackId =
                    textAssessmentConflict['firstFeedback'].id === feedback.id ? textAssessmentConflict['secondFeedback'].id : textAssessmentConflict['firstFeedback'].id;
            });
        });
    }

    /**
     * Match given text blocks and feedback items by text block references.
     * @param blocks list of text blocks of type {TextBlock[]}
     * @param feedbacks list of feedback made during assessment of type {Feedback[]}
     */
    public static matchBlocksWithFeedbacks(blocks: TextBlock[], feedbacks: Feedback[]): TextBlockRef[] {
        return blocks.map(
            (block: TextBlock) =>
                new TextBlockRef(
                    block,
                    feedbacks.find(({ reference }) => block.id === reference),
                ),
        );
    }

    /**
     * Track the change of the Feedback in Athene.
     *
     * The routing to athene is done using nginx on the production server.
     *
     * @param submission - The submission object that holds the data that is tracked
     * @param origin - The method that calls the the tracking method
     */
    public trackAssessment(submission?: TextSubmission, origin?: string) {
        if (submission?.atheneTextAssessmentTrackingToken) {
            // clone submission and resolve circular json properties
            const submissionForSending = cloneDeep(submission);
            if (submissionForSending.participation) {
                submissionForSending.participation.submissions = [];
                if (submissionForSending.participation.exercise) {
                    submissionForSending.participation.exercise.course = undefined;
                    submissionForSending.participation.exercise.exerciseGroup = undefined;
                }
            }
            submissionForSending.atheneTextAssessmentTrackingToken = undefined;

            // eslint-disable-next-line chai-friendly/no-unused-expressions
            submissionForSending.participation?.results!.forEach((result) => {
                result.participation = undefined;
                result.submission = undefined;
            });

            const trackingObject = {
                origin,
                textBlocks: submissionForSending.blocks,
                participation: submissionForSending.participation,
            };

            // The request is directly routed to athene via nginx
            this.http
                .post(`${SERVER_API_URL}/athene-tracking/text-exercise-assessment`, trackingObject, {
                    headers: { 'X-Athene-Tracking-Authorization': submission.atheneTextAssessmentTrackingToken },
                })
                .subscribe();
        }
    }

    /**
     * Connects the participation with the submission and result
     *
     *  @param participation
     *  @param submission
     *  @param result
     */
    private static reconnectResultsParticipation(participation: Participation, submission: Submission, result: Result) {
        setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
        submission.participation = participation;
        participation.results = submission.results!;
        result.submission = submission;
        result.participation = participation;
        // Make sure Feedbacks Array is initialized
        result.feedbacks = result.feedbacks || [];
        TextAssessmentService.convertFeedbackConflictsFromServer(result.feedbacks);
    }
}
