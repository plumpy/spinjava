package com.netflix.bluespar.orca.bakery.tasks

import com.netflix.bluespar.orca.bakery.api.BakeState
import com.netflix.bluespar.orca.bakery.api.BakeStatus
import com.netflix.bluespar.orca.bakery.api.BakeryService
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.util.UUID.randomUUID

class MonitorBakeTaskSpec extends Specification {

    @Subject
    def task = new MonitorBakeTask()

    final region = "us-west-1"
    def jobParameters = new JobParametersBuilder().addString("region", region).toJobParameters()
    def jobExecution = new JobExecution(1, jobParameters)
    def stepExecution = new StepExecution("bakeStep", jobExecution)
    def stepContext = new StepContext(stepExecution)
    def stepContribution = new StepContribution(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    @Unroll
    def "should return #repeatStatus if bake is #bakeState"() {
        given:
        def previousStatus = new BakeStatus(id: id, state: BakeState.PENDING)
        jobExecution.executionContext.put("bake.status", previousStatus)

        and:
        task.bakery = Stub(BakeryService) {
            lookupStatus(region, id) >> Observable.from(new BakeStatus(id: id, state: bakeState))
        }

        expect:
        task.execute(stepContribution, chunkContext) == repeatStatus

        where:
        bakeState           | repeatStatus
        BakeState.PENDING   | RepeatStatus.CONTINUABLE
        BakeState.RUNNING   | RepeatStatus.CONTINUABLE
        BakeState.COMPLETED | RepeatStatus.FINISHED
        BakeState.CANCELLED | RepeatStatus.FINISHED
        BakeState.SUSPENDED | RepeatStatus.CONTINUABLE

        id = randomUUID().toString()
    }

    def "should store the updated status in the job context"() {
        given:
        def previousStatus = new BakeStatus(id: id, state: BakeState.PENDING)
        jobExecution.executionContext.put("bake.status", previousStatus)

        and:
        task.bakery = Stub(BakeryService) {
            lookupStatus(region, id) >> Observable.from(new BakeStatus(id: id, state: BakeState.COMPLETED))
        }

        when:
        task.execute(stepContribution, chunkContext)

        then:
        with(stepContext.jobExecutionContext["bake.status"]) {
            id == previousStatus.id
            state == BakeState.COMPLETED
        }

        where:
        id = randomUUID().toString()
    }

}
