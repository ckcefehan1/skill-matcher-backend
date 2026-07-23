package org.efehan.skillmatcherbackend.fixtures.builder

import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel

class UserBuilder {
    fun build(
        email: String = "max@firma.de",
        passwordHash: String? = "hashed",
        firstName: String? = "Max",
        lastName: String? = "Mustermann",
        role: RoleModel = RoleBuilder().build(),
        isEnabled: Boolean = true,
        maxConcurrentProjects: Int = 3,
    ): UserModel {
        val user =
            UserModel(
                email = email,
                passwordHash = passwordHash,
                firstName = firstName,
                lastName = lastName,
                role = role,
                maxConcurrentProjects = maxConcurrentProjects,
            )
        user.isEnabled = isEnabled
        return user
    }
}
