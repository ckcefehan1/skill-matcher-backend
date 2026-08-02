package org.efehan.skillmatcherbackend.core.role

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.RoleName

data class CreateRoleRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 50, message = "name must not exceed 50 characters")
    // the name becomes the ROLE_ authority, so anything outside this alphabet would break hasRole()
    @field:Pattern(
        regexp = "[A-Za-z][A-Za-z0-9_]*",
        message = "name must start with a letter and contain only letters, digits and underscores",
    )
    val name: String,
    @field:Size(max = 255, message = "description must not exceed 255 characters")
    val description: String?,
)

data class UpdateRoleRequest(
    @field:Size(max = 255, message = "description must not exceed 255 characters")
    val description: String?,
)

data class RoleDto(
    val id: String,
    val name: String,
    val description: String?,
    val builtIn: Boolean,
)

fun RoleModel.toDTO() =
    RoleDto(
        id = id,
        name = name,
        description = description,
        builtIn = RoleName.isBuiltIn(name),
    )
