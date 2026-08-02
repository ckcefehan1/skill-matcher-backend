package org.efehan.skillmatcherbackend.exception

enum class GlobalErrorCode(
    val description: String,
) {
    // Authentication
    BAD_CREDENTIALS("Bad credentials."),
    FORBIDDEN("Forbidden."),
    PROJECT_ACCESS_DENIED("Not allowed to modify this project."),
    USER_SKILL_ACCESS_DENIED("Not allowed to modify this user skill."),
    PROJECT_SKILL_ACCESS_DENIED("Not allowed to modify this project skill."),
    USER_MUST_LOGIN("User must be logged in."),
    CONVERSATION_ACCESS_DENIED("Not allowed to access this conversation."),

    // Validation
    VALIDATION_ERROR("Request validation failed."),
    MALFORMED_REQUEST("Malformed or unreadable request body."),
    INVALID_PASSWORD("Invalid password."),
    INVALID_REFRESH_TOKEN("Invalid refresh token."),

    // Duplicate entries
    DUPLICATE_ENTRY("A resource with the same unique value already exists."),
    USER_ALREADY_EXISTS("User already exists."),
    ROLE_ALREADY_EXISTS("Role already exists."),

    // Roles
    ROLE_IN_USE("Role is still assigned to users."),
    ROLE_IMMUTABLE("Built-in roles cannot be deleted."),

    // Not Found
    NOT_FOUND("A resource with the value could not be found."),
    ROLE_NOT_FOUND("Role could not be found."),
    USER_NOT_FOUND("User could not be found."),
    REFRESH_TOKEN_NOT_FOUND("Refresh token could not be found."),
    PROJECT_NOT_FOUND("Project could not be found."),
    SKILL_NOT_FOUND("Skill could not be found."),
    USER_SKILL_NOT_FOUND("User skill could not be found."),
    CONVERSATION_NOT_FOUND("Conversation could not be found."),
    NOTIFICATION_NOT_FOUND("Notification could not be found."),
    COMPANY_NOT_FOUND("Company could not be found."),

    // Matching
    PROJECT_SKILL_NOT_FOUND("Project skill could not be found."),
    PROJECT_SKILL_DUPLICATE("This skill is already assigned to the project."),

    // Skill Relations
    SKILL_RELATION_NOT_FOUND("Skill relation could not be found."),
    SKILL_RELATION_DUPLICATE("A relation between these skills already exists."),

    // Project Members
    PROJECT_MEMBER_NOT_FOUND("Project member could not be found."),
    PROJECT_MEMBER_DUPLICATE("User is already a member of this project."),
    PROJECT_MEMBER_REQUIRES_ACCEPTED_APPLICATION("User can only be added after an accepted application."),
    PROJECT_FULL("Project has reached its maximum number of members."),

    // Project Applications
    APPLICATION_NOT_FOUND("Project application could not be found."),
    APPLICATION_DUPLICATE("An active application for this project already exists."),
    APPLICATION_ALREADY_DECIDED("This application has already been decided."),
    APPLICATION_ACCESS_DENIED("Not allowed to access this application."),
    APPLICATION_FOR_MEMBER("User is already an active member of this project."),

    // User Availability
    USER_AVAILABILITY_NOT_FOUND("User availability entry could not be found."),
    USER_AVAILABILITY_ACCESS_DENIED("Not allowed to modify this availability entry."),
    USER_AVAILABILITY_OVERLAP("Availability period overlaps with an existing entry."),

    // Invitation
    INVALID_INVITATION_TOKEN("Invitation token is invalid."),
    INVITATION_ALREADY_ACCEPTED("Invitation has already been accepted."),
    INVITATION_TOKEN_EXPIRED("Invitation token has expired."),
    INVALID_REGISTRATION_CODE("Registration code is invalid."),

    // Password Reset
    INVALID_PASSWORD_RESET_TOKEN("Invalid password reset token."),
    PASSWORD_RESET_TOKEN_EXPIRED("Password reset token has expired."),
    PASSWORD_RESET_TOKEN_USED("Password reset token has already been used."),

    // User state
    USER_INVALID_OPERATION("Current user state does not allow this operation."),
    ACCOUNT_DISABLED("Account is disabled."),
    ACCOUNT_LOCKED("Account is temporarily locked due to failed login attempts."),
    MUST_CHANGE_PASSWORD("Password change required."),

    // General
    RATE_LIMIT_EXCEEDED("Too many requests. Please try again later."),
    INTERNAL_SERVER_ERROR("An unexpected error occurred."),
}
