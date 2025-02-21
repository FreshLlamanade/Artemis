package de.tum.in.www1.artemis.domain.quiz.scoring;

import de.tum.in.www1.artemis.domain.quiz.*;

/**
 * All or nothing means the full score is given if the answer is 100% correct, otherwise a score of 0 is given
 */
public class ScoringStrategyShortAnswerAllOrNothing implements ScoringStrategy {

    @Override
    public double calculateScore(QuizQuestion quizQuestion, SubmittedAnswer submittedAnswer) {
        // return maximal Score if the quizQuestion is invalid
        if (quizQuestion.isInvalid()) {
            return quizQuestion.getPoints();
        }
        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer && quizQuestion instanceof ShortAnswerQuestion) {
            ShortAnswerSubmittedAnswer shortAnswerAnswer = (ShortAnswerSubmittedAnswer) submittedAnswer;
            ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) quizQuestion;

            int[] values = ScoringStrategyShortAnswerUtil.getCorrectAndIncorrectSolutionCount(shortAnswerQuestion, shortAnswerAnswer);
            int correctSolutionsCount = values[0];

            if (correctSolutionsCount == shortAnswerQuestion.getSpots().size()) {
                return shortAnswerQuestion.getPoints();
            }
            else {
                return 0.0;
            }
        }
        // the submitted answer's type doesn't fit the quizQuestion's type => it cannot be correct
        return 0.0;
    }
}
