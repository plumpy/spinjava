package com.netflix.bluespar.orca.batch

import com.netflix.bluespar.orca.Step
import groovy.transform.CompileStatic
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus

import static com.netflix.bluespar.orca.StepResult.COMPLETE
import static org.springframework.batch.repeat.RepeatStatus.CONTINUABLE
import static org.springframework.batch.repeat.RepeatStatus.FINISHED

@CompileStatic
class StepTasklet implements Tasklet {

    private final Step step

    StepTasklet(Step step) {
        this.step = step
    }

    @Override
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        def result = step.execute()

        result == COMPLETE ? FINISHED : CONTINUABLE
    }
}
