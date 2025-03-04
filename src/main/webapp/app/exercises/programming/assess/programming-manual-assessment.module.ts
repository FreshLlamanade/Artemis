import { NgModule } from '@angular/core';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, FormDateTimePickerModule, ArtemisAssessmentSharedModule],
    declarations: [CodeEditorTutorAssessmentInlineFeedbackComponent],
    exports: [CodeEditorTutorAssessmentInlineFeedbackComponent],
})
export class ArtemisProgrammingManualAssessmentModule {}
