package org.efehan.skillmatcherbackend.core.application

import jakarta.validation.constraints.Size
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationModel
import java.time.Instant

data class CreateApplicationRequest(
    @field:Size(max = 1000, message = "Message must not exceed 1000 characters")
    val message: String? = null,
)

data class DecideApplicationRequest(
    @field:Size(max = 1000, message = "Reason must not exceed 1000 characters")
    val reason: String? = null,
)

data class CreateInvitationRequest(
    val userId: String,
    @field:Size(max = 1000, message = "Message must not exceed 1000 characters")
    val message: String? = null,
)

data class ApplicationDto(
    val id: String,
    val projectId: String,
    val projectName: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val status: String,
    val appliedAt: Instant,
    val decidedAt: Instant?,
    val decidedById: String?,
    val decidedByName: String?,
    val message: String?,
)

fun ProjectApplicationModel.toDTO(): ApplicationDto =
    ApplicationDto(
        id = id,
        projectId = project.id,
        projectName = project.name,
        userId = user.id,
        userName = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim(),
        userEmail = user.email,
        status = status.name,
        appliedAt = appliedAt,
        decidedAt = decidedAt,
        decidedById = decidedBy?.id,
        decidedByName =
            decidedBy?.let {
                "${it.firstName ?: ""} ${it.lastName ?: ""}".trim()
            },
        message = message,
    )
