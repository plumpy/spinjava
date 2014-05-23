package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.Bake.Label
import com.netflix.spinnaker.orca.bakery.api.Bake.OperatingSystem
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class CreateBakeTask implements Task {

    @Autowired
    BakeryService bakery

    @Override
    TaskResult execute(TaskContext context) {
        def region = context["region"] as String
        def bake = bakeFromContext(context)

        def bakeStatus = bakery.createBake(region, bake).toBlockingObservable().single()

        def taskResult = new TaskResult()
        taskResult.outputs["bake.status"] = bakeStatus
        taskResult.status = TaskResult.Status.SUCCEEDED
        return taskResult
    }

    private Bake bakeFromContext(TaskContext context) {
        // TODO: use a Groovy 2.3 @Builder
        new Bake(context["bake.user"] as String,
            context["bake.package"] as String,
            Label.valueOf(context["bake.baseLabel"] as String),
            OperatingSystem.valueOf(context["bake.baseOs"] as String)
        )
    }
}
