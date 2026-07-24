package org.efehan.skillmatcherbackend.core.project

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.time.LocalDate

data class CreateProjectRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val description: String,
    @field:NotNull
    var startDate: LocalDate,
    @field:NotNull
    var endDate: LocalDate,
    @field:Min(1)
    val maxMembers: Int,
)

data class UpdateProjectRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val description: String,
    @field:NotNull
    @field:Pattern(regexp = "PLANNED|ACTIVE|PAUSED|COMPLETED", message = "Status must be one of: PLANNED, ACTIVE, PAUSED, COMPLETED")
    val status: String,
    @field:NotNull
    val startDate: LocalDate,
    @field:NotNull
    val endDate: LocalDate,
    @field:Min(1)
    val maxMembers: Int,
)

data class ProjectDto(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val maxMembers: Int,
    val ownerId: String,
    val ownerName: String,
    val createdDate: Instant?,
)
