import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SERVER_API_URL } from 'app/app.constants';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Injectable({ providedIn: 'root' })
export class QuizReEvaluateService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-exercises-re-evaluate';

    constructor(private http: HttpClient) {}

    /**
     * Update a QuizExercise.
     * @param quizExercise quizExercise
     */
    update(quizExercise: QuizExercise) {
        const copy = this.convert(quizExercise);
        return this.http.put<QuizExercise>(this.resourceUrl, copy, { observe: 'response' });
    }

    /**
     * Convert a QuizExercise to a JSON which can be sent to the server.
     * @param quizExercise quizExercise
     */
    private convert(quizExercise: QuizExercise): QuizExercise {
        const copy: QuizExercise = Object.assign({}, quizExercise);
        return copy;
    }
}
