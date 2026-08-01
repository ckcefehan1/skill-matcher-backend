package org.efehan.skillmatcherbackend.api.exception

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Global Error Code Unit Tests")
class GlobalErrorCodeTest {
    // Authentication
    val expectedBadCredentialsErrorCode = "BAD_CREDENTIALS"
    val expectedForbiddenErrorCode = "FORBIDDEN"
    val expectedProjectAccessDeniedErrorCode = "PROJECT_ACCESS_DENIED"
    val expectedProjectSkillAccessDeniedErrorCode = "PROJECT_SKILL_ACCESS_DENIED"
    val expectedUserSkillAccessDeniedErrorCode = "USER_SKILL_ACCESS_DENIED"
    val expectedUserMustLoginErrorCode = "USER_MUST_LOGIN"

    // Validation
    val expectedValidationErrorCode = "VALIDATION_ERROR"
    val expectedMalformedRequestErrorCode = "MALFORMED_REQUEST"
    val expectedInvalidPasswordErrorCode = "INVALID_PASSWORD"
    val expectedInvalidRefreshTokenErrorCode = "INVALID_REFRESH_TOKEN"

    // Duplicate entries
    val expectedDuplicateEntryErrorCode = "DUPLICATE_ENTRY"
    val expectedUserAlreadyExistsErrorCode = "USER_ALREADY_EXISTS"

    // Not Found
    val expectedNotFoundErrorCode = "NOT_FOUND"
    val expectedRoleNotFoundErrorCode = "ROLE_NOT_FOUND"
    val expectedUserNotFoundErrorCode = "USER_NOT_FOUND"
    val expectedRefreshTokenNotFoundErrorCode = "REFRESH_TOKEN_NOT_FOUND"
    val expectedProjectNotFoundErrorCode = "PROJECT_NOT_FOUND"
    val expectedSkillNotFoundErrorCode = "SKILL_NOT_FOUND"
    val expectedUserSkillNotFoundErrorCode = "USER_SKILL_NOT_FOUND"

    // Invitation
    val expectedInvalidInvitationTokenErrorCode = "INVALID_INVITATION_TOKEN"
    val expectedInvitationAlreadyAcceptedErrorCode = "INVITATION_ALREADY_ACCEPTED"
    val expectedInvitationTokenExpiredErrorCode = "INVITATION_TOKEN_EXPIRED"

    // Password Reset
    val expectedInvalidPasswordResetTokenErrorCode = "INVALID_PASSWORD_RESET_TOKEN"
    val expectedPasswordResetTokenExpiredErrorCode = "PASSWORD_RESET_TOKEN_EXPIRED"
    val expectedPasswordResetTokenUsedErrorCode = "PASSWORD_RESET_TOKEN_USED"

    // Matching
    val expectedProjectSkillNotFoundErrorCode = "PROJECT_SKILL_NOT_FOUND"
    val expectedProjectSkillDuplicateErrorCode = "PROJECT_SKILL_DUPLICATE"

    // Skill Relations
    val expectedSkillRelationNotFoundErrorCode = "SKILL_RELATION_NOT_FOUND"
    val expectedSkillRelationDuplicateErrorCode = "SKILL_RELATION_DUPLICATE"

    // Project Members
    val expectedProjectMemberNotFoundErrorCode = "PROJECT_MEMBER_NOT_FOUND"
    val expectedProjectMemberDuplicateErrorCode = "PROJECT_MEMBER_DUPLICATE"
    val expectedProjectFullErrorCode = "PROJECT_FULL"

    // Project Applications
    val expectedApplicationNotFoundErrorCode = "APPLICATION_NOT_FOUND"
    val expectedApplicationDuplicateErrorCode = "APPLICATION_DUPLICATE"
    val expectedApplicationAlreadyDecidedErrorCode = "APPLICATION_ALREADY_DECIDED"
    val expectedApplicationAccessDeniedErrorCode = "APPLICATION_ACCESS_DENIED"
    val expectedApplicationForMemberErrorCode = "APPLICATION_FOR_MEMBER"

    // User Availability
    val expectedUserAvailabilityNotFoundErrorCode = "USER_AVAILABILITY_NOT_FOUND"
    val expectedUserAvailabilityAccessDeniedErrorCode = "USER_AVAILABILITY_ACCESS_DENIED"
    val expectedUserAvailabilityOverlapErrorCode = "USER_AVAILABILITY_OVERLAP"

    // User state
    val expectedUserInvalidOperationErrorCode = "USER_INVALID_OPERATION"
    val expectedAccountDisabledErrorCode = "ACCOUNT_DISABLED"
    val expectedAccountLockedErrorCode = "ACCOUNT_LOCKED"
    val expectedMustChangePasswordErrorCode = "MUST_CHANGE_PASSWORD"

    // Chat
    val expectedConversationNotFoundErrorCode = "CONVERSATION_NOT_FOUND"
    val expectedConversationAccessDeniedErrorCode = "CONVERSATION_ACCESS_DENIED"

    // Notifications
    val expectedNotificationNotFoundErrorCode = "NOTIFICATION_NOT_FOUND"

    // General
    val expectedRateLimitExceededErrorCode = "RATE_LIMIT_EXCEEDED"
    val expectedInternalServerErrorCode = "INTERNAL_SERVER_ERROR"

    @Test
    fun `should not change global error codes otherwise api contract breaks`() {
        // Authentication
        assertThat(expectedBadCredentialsErrorCode).isEqualTo(GlobalErrorCode.BAD_CREDENTIALS.name)
        assertThat(expectedForbiddenErrorCode).isEqualTo(GlobalErrorCode.FORBIDDEN.name)
        assertThat(expectedProjectAccessDeniedErrorCode).isEqualTo(GlobalErrorCode.PROJECT_ACCESS_DENIED.name)
        assertThat(expectedProjectSkillAccessDeniedErrorCode).isEqualTo(GlobalErrorCode.PROJECT_SKILL_ACCESS_DENIED.name)
        assertThat(expectedUserSkillAccessDeniedErrorCode).isEqualTo(GlobalErrorCode.USER_SKILL_ACCESS_DENIED.name)
        assertThat(expectedUserMustLoginErrorCode).isEqualTo(GlobalErrorCode.USER_MUST_LOGIN.name)

        // Validation
        assertThat(expectedValidationErrorCode).isEqualTo(GlobalErrorCode.VALIDATION_ERROR.name)
        assertThat(expectedMalformedRequestErrorCode).isEqualTo(GlobalErrorCode.MALFORMED_REQUEST.name)
        assertThat(expectedInvalidPasswordErrorCode).isEqualTo(GlobalErrorCode.INVALID_PASSWORD.name)
        assertThat(expectedInvalidRefreshTokenErrorCode).isEqualTo(GlobalErrorCode.INVALID_REFRESH_TOKEN.name)

        // Duplicate entries
        assertThat(expectedDuplicateEntryErrorCode).isEqualTo(GlobalErrorCode.DUPLICATE_ENTRY.name)
        assertThat(expectedUserAlreadyExistsErrorCode).isEqualTo(GlobalErrorCode.USER_ALREADY_EXISTS.name)

        // Not Found
        assertThat(expectedNotFoundErrorCode).isEqualTo(GlobalErrorCode.NOT_FOUND.name)
        assertThat(expectedRoleNotFoundErrorCode).isEqualTo(GlobalErrorCode.ROLE_NOT_FOUND.name)
        assertThat(expectedUserNotFoundErrorCode).isEqualTo(GlobalErrorCode.USER_NOT_FOUND.name)
        assertThat(expectedRefreshTokenNotFoundErrorCode).isEqualTo(GlobalErrorCode.REFRESH_TOKEN_NOT_FOUND.name)
        assertThat(expectedProjectNotFoundErrorCode).isEqualTo(GlobalErrorCode.PROJECT_NOT_FOUND.name)
        assertThat(expectedSkillNotFoundErrorCode).isEqualTo(GlobalErrorCode.SKILL_NOT_FOUND.name)
        assertThat(expectedUserSkillNotFoundErrorCode).isEqualTo(GlobalErrorCode.USER_SKILL_NOT_FOUND.name)

        // Invitation
        assertThat(expectedInvalidInvitationTokenErrorCode).isEqualTo(GlobalErrorCode.INVALID_INVITATION_TOKEN.name)
        assertThat(expectedInvitationAlreadyAcceptedErrorCode).isEqualTo(GlobalErrorCode.INVITATION_ALREADY_ACCEPTED.name)
        assertThat(expectedInvitationTokenExpiredErrorCode).isEqualTo(GlobalErrorCode.INVITATION_TOKEN_EXPIRED.name)

        // Password Reset
        assertThat(expectedInvalidPasswordResetTokenErrorCode).isEqualTo(GlobalErrorCode.INVALID_PASSWORD_RESET_TOKEN.name)
        assertThat(expectedPasswordResetTokenExpiredErrorCode).isEqualTo(GlobalErrorCode.PASSWORD_RESET_TOKEN_EXPIRED.name)
        assertThat(expectedPasswordResetTokenUsedErrorCode).isEqualTo(GlobalErrorCode.PASSWORD_RESET_TOKEN_USED.name)

        // Matching
        assertThat(expectedProjectSkillNotFoundErrorCode).isEqualTo(GlobalErrorCode.PROJECT_SKILL_NOT_FOUND.name)
        assertThat(expectedProjectSkillDuplicateErrorCode).isEqualTo(GlobalErrorCode.PROJECT_SKILL_DUPLICATE.name)

        // Skill Relations
        assertThat(expectedSkillRelationNotFoundErrorCode).isEqualTo(GlobalErrorCode.SKILL_RELATION_NOT_FOUND.name)
        assertThat(expectedSkillRelationDuplicateErrorCode).isEqualTo(GlobalErrorCode.SKILL_RELATION_DUPLICATE.name)

        // Project Members
        assertThat(expectedProjectMemberNotFoundErrorCode).isEqualTo(GlobalErrorCode.PROJECT_MEMBER_NOT_FOUND.name)
        assertThat(expectedProjectMemberDuplicateErrorCode).isEqualTo(GlobalErrorCode.PROJECT_MEMBER_DUPLICATE.name)
        assertThat(expectedProjectFullErrorCode).isEqualTo(GlobalErrorCode.PROJECT_FULL.name)

        // Project Applications
        assertThat(expectedApplicationNotFoundErrorCode).isEqualTo(GlobalErrorCode.APPLICATION_NOT_FOUND.name)
        assertThat(expectedApplicationDuplicateErrorCode).isEqualTo(GlobalErrorCode.APPLICATION_DUPLICATE.name)
        assertThat(expectedApplicationAlreadyDecidedErrorCode).isEqualTo(GlobalErrorCode.APPLICATION_ALREADY_DECIDED.name)
        assertThat(expectedApplicationAccessDeniedErrorCode).isEqualTo(GlobalErrorCode.APPLICATION_ACCESS_DENIED.name)
        assertThat(expectedApplicationForMemberErrorCode).isEqualTo(GlobalErrorCode.APPLICATION_FOR_MEMBER.name)

        // User Availability
        assertThat(expectedUserAvailabilityNotFoundErrorCode).isEqualTo(GlobalErrorCode.USER_AVAILABILITY_NOT_FOUND.name)
        assertThat(expectedUserAvailabilityAccessDeniedErrorCode).isEqualTo(GlobalErrorCode.USER_AVAILABILITY_ACCESS_DENIED.name)
        assertThat(expectedUserAvailabilityOverlapErrorCode).isEqualTo(GlobalErrorCode.USER_AVAILABILITY_OVERLAP.name)

        // User state
        assertThat(expectedUserInvalidOperationErrorCode).isEqualTo(GlobalErrorCode.USER_INVALID_OPERATION.name)
        assertThat(expectedAccountDisabledErrorCode).isEqualTo(GlobalErrorCode.ACCOUNT_DISABLED.name)
        assertThat(expectedAccountLockedErrorCode).isEqualTo(GlobalErrorCode.ACCOUNT_LOCKED.name)
        assertThat(expectedMustChangePasswordErrorCode).isEqualTo(GlobalErrorCode.MUST_CHANGE_PASSWORD.name)

        // Chat
        assertThat(expectedConversationNotFoundErrorCode).isEqualTo(GlobalErrorCode.CONVERSATION_NOT_FOUND.name)
        assertThat(expectedConversationAccessDeniedErrorCode).isEqualTo(GlobalErrorCode.CONVERSATION_ACCESS_DENIED.name)

        // Notifications
        assertThat(expectedNotificationNotFoundErrorCode).isEqualTo(GlobalErrorCode.NOTIFICATION_NOT_FOUND.name)

        // General
        assertThat(expectedRateLimitExceededErrorCode).isEqualTo(GlobalErrorCode.RATE_LIMIT_EXCEEDED.name)
        assertThat(expectedInternalServerErrorCode).isEqualTo(GlobalErrorCode.INTERNAL_SERVER_ERROR.name)

        assertThat(GlobalErrorCode.entries.size).isEqualTo(51)
    }

    @Test
    fun `all error codes should have non-blank descriptions`() {
        GlobalErrorCode.entries.forEach { code ->
            assertThat(code.description).isNotBlank()
        }
    }

    @Test
    fun `all error codes should have descriptions ending with period`() {
        GlobalErrorCode.entries.forEach { code ->
            assertThat(code.description).endsWith(".")
        }
    }
}
