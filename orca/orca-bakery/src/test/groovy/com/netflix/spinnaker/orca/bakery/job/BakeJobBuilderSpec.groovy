package com.netflix.spinnaker.orca.bakery.job

import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.SimpleJob
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.tasklet.TaskletStep
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.support.StaticApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification
import spock.lang.Subject

class BakeJobBuilderSpec extends Specification {

    @Subject builder = new BakeJobBuilder()

    def applicationContext = new StaticApplicationContext()
    def txMan = Stub(PlatformTransactionManager)
    def repository = Stub(JobRepository)
    def jobs = new JobBuilderFactory(repository)
    def steps = new StepBuilderFactory(repository, txMan)

    def bakery = Mock(BakeryService)

    def setup() {
        registerMockBean("bakery", bakery)

        builder.steps = steps
        builder.applicationContext = applicationContext
    }

    def "builds a bake workflow"() {
        when:
        def job = builder.build(jobs.get("BakeJobBuilderSpecJob")).build()

        then:
        job instanceof SimpleJob
        def steps = job.stepNames.collect {
            job.getStep(it)
        }
        steps.size() == 2
        steps.every { it instanceof TaskletStep }
        steps.every { ((TaskletStep)it).tasklet instanceof TaskTaskletAdapter }
        steps.tasklet.taskType == [CreateBakeTask, MonitorBakeTask]
    }

    private void registerMockBean(String name, bean) {
        applicationContext.registerBeanDefinition name, new GenericBeanDefinition(source: bean)
    }
}
