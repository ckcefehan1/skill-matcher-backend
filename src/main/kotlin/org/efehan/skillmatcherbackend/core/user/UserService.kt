package org.efehan.skillmatcherbackend.core.user

import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService(
    private val userRepo: UserRepository,
) {
    fun getUser(userId: String): UserModel =
        userRepo.findByIdOrNull(userId)
            ?: throw EntryNotFoundException(
                resource = "User",
                field = "id",
                value = userId,
                errorCode = GlobalErrorCode.USER_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )
}
