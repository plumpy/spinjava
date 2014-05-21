package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution

class TaskAdapterTaskletSpec extends Specification {

    def step = Mock(Task)

    @Subject
    def tasklet = new TaskAdapterTasklet(step)

    def stepExecution = createStepExecution()
    def stepContext = new StepContext(stepExecution)
    def stepContribution = new StepContribution(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def "should invoke the step when executed"() {
        when:
        tasklet.execute(stepContribution, chunkContext)

        then:
        1 * step.execute(*_) >> TaskResult.SUCCEEDED
    }

    @Unroll("should convert a result of #taskResult to repeat status #repeatStatus and exitStatus #exitStatus")
    def "should convert step return status to equivalent batch status"() {
        given:
        step.execute(*_) >> taskResult

        expect:
        tasklet.execute(stepContribution, chunkContext) == repeatStatus

        and:
        stepContribution.exitStatus == exitStatus

        where:
        taskResult           | repeatStatus             | exitStatus
        TaskResult.SUCCEEDED | RepeatStatus.FINISHED    | ExitStatus.COMPLETED
        TaskResult.FAILED    | RepeatStatus.FINISHED    | ExitStatus.FAILED
        TaskResult.RUNNING   | RepeatStatus.CONTINUABLE | ExitStatus.EXECUTING
    }

}
