import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import * as moment from 'moment';
import { isMoment } from 'moment';
import { Result } from '../../../entities/result.model';
import { Moment } from 'moment';
import { createRequestOption } from 'app/shared/util/request-util';
import { Feedback } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { addUserIndependentRepositoryUrl } from 'app/overview/participation-utils';
import { map, tap } from 'rxjs/operators';

export type EntityResponseType = HttpResponse<Result>;
export type EntityArrayResponseType = HttpResponse<Result[]>;

export interface IResultService {
    find: (id: number) => Observable<EntityResponseType>;
    findBySubmissionId: (submissionId: number) => Observable<EntityResponseType>;
    getResultsForExercise: (courseId: number, exerciseId: number, req?: any) => Observable<EntityArrayResponseType>;
    getLatestResultWithFeedbacks: (particpationId: number) => Observable<HttpResponse<Result>>;
    getFeedbackDetailsForResult: (resultId: number) => Observable<HttpResponse<Feedback[]>>;
    delete: (id: number) => Observable<HttpResponse<void>>;
}

@Injectable({ providedIn: 'root' })
export class ResultService implements IResultService {
    private exerciseResourceUrl = SERVER_API_URL + 'api/exercises';
    private resultResourceUrl = SERVER_API_URL + 'api/results';
    private submissionResourceUrl = SERVER_API_URL + 'api/submissions';
    private participationResourceUrl = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    find(resultId: number): Observable<EntityResponseType> {
        return this.http.get<Result>(`${this.resultResourceUrl}/${resultId}`, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/submission/${submissionId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    getResultsForExercise(exerciseId: number, req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Result[]>(`${this.exerciseResourceUrl}/${exerciseId}/results`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => this.convertArrayResponse(res)));
    }

    getFeedbackDetailsForResult(resultId: number): Observable<HttpResponse<Feedback[]>> {
        return this.http.get<Feedback[]>(`${this.resultResourceUrl}/${resultId}/details`, { observe: 'response' });
    }

    getLatestResultWithFeedbacks(particpationId: number): Observable<HttpResponse<Result>> {
        return this.http.get<Result>(`${this.participationResourceUrl}/${particpationId}/latest-result`, { observe: 'response' });
    }

    delete(resultId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resultResourceUrl}/${resultId}`, { observe: 'response' });
    }

    /**
     * Create a new example result for the provided submission ID.
     *
     * @param submissionId The ID of the example submission for which a result should get created
     * @return The newly created (and empty) example result
     */
    createNewExampleResult(submissionId: number): Observable<HttpResponse<Result>> {
        return this.http.post<Result>(`${this.submissionResourceUrl}/${submissionId}/example-result`, null, { observe: 'response' });
    }

    public convertDateFromClient(result: Result): Result {
        const copy: Result = Object.assign({}, result, {
            completionDate:
                // Result completionDate is a moment object -> toJSON.
                result.completionDate && isMoment(result.completionDate)
                    ? result.completionDate.toJSON()
                    : // Result completionDate would be a valid date -> keep string.
                    result.completionDate && moment(result.completionDate).isValid()
                    ? result.completionDate
                    : // No valid date -> remove date.
                      null,
        });
        return copy;
    }

    protected convertArrayResponse(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((result: Result) => {
                result.completionDate = result.completionDate ? moment(result.completionDate) : undefined;
                result.participation = this.convertParticipationDateFromServer(result.participation! as StudentParticipation);
            });
        }
        return res;
    }

    public convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.completionDate = res.body.completionDate ? moment(res.body.completionDate) : undefined;
            res.body.participation = this.convertParticipationDateFromServer(res.body.participation! as StudentParticipation);
        }
        return res;
    }

    convertParticipationDateFromServer(participation: StudentParticipation) {
        if (participation) {
            participation.initializationDate = participation.initializationDate ? moment(participation.initializationDate) : undefined;
            if (participation.exercise) {
                participation.exercise = this.exerciseService.convertExerciseDateFromServer(participation.exercise);
            }
        }
        return participation;
    }

    /**
     * Fetches all results for an exercise and returns them
     */
    getResults(exercise: Exercise) {
        return this.getResultsForExercise(exercise.id!, {
            withSubmissions: exercise.type === ExerciseType.MODELING,
        }).pipe(
            tap((res: HttpResponse<Result[]>) => {
                return res.body!.map((result) => {
                    result.participation!.results = [result];
                    (result.participation! as StudentParticipation).exercise = exercise;
                    if (result.participation!.type === ParticipationType.PROGRAMMING) {
                        addUserIndependentRepositoryUrl(result.participation!);
                    }
                    result.durationInMinutes = this.durationInMinutes(
                        result.completionDate!,
                        result.participation!.initializationDate ? result.participation!.initializationDate : exercise.releaseDate!,
                    );
                    // Nest submission into participation so that it is available for the result component
                    if (result.submission) {
                        result.participation!.submissions = [result.submission];
                    }
                    return result;
                });
            }),
        );
    }

    /**
     * Utility function
     */
    private durationInMinutes(completionDate: Moment, initializationDate: Moment) {
        return moment(completionDate).diff(initializationDate, 'minutes');
    }

    /**
     * Utility function used to trigger the download of a CSV file
     */
    public triggerDownloadCSV(rows: String[], csvFileName: String) {
        const csvContent = rows.join('\n');
        const encodedUri = encodeURI(csvContent);
        const link = document.createElement('a');
        link.setAttribute('href', encodedUri);
        link.setAttribute('download', `${csvFileName}.csv`);
        document.body.appendChild(link); // Required for FF
        link.click();
    }
}
