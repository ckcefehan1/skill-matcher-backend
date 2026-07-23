package org.efehan.skillmatcherbackend.fixtures.builder

import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationModel
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import java.time.Instant

class ProjectApplicationBuilder {
    fun build(
        project: ProjectModel = ProjectBuilder().build(),
        user: UserModel = UserBuilder().build(),
        status: ApplicationStatus = ApplicationStatus.PENDING,
        appliedAt: Instant = Instant.now(),
        decidedAt: Instant? = null,
        decidedBy: UserModel? = null,
        message: String? = null,
    ): ProjectApplicationModel =
        ProjectApplicationModel(
            project = project,
            user = user,
            status = status,
            appliedAt = appliedAt,
            decidedAt = decidedAt,
            decidedBy = decidedBy,
            message = message,
        )
}
