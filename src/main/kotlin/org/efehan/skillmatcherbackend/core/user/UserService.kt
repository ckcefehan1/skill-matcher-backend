package org.efehan.skillmatcherbackend.core.user

import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
        findById(userId)
            ?: throw EntryNotFoundException(
                resource = "User",
                field = "id",
                value = userId,
                errorCode = GlobalErrorCode.USER_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )

    fun findById(userId: String): UserModel? = userRepo.findByIdOrNull(userId)

    fun findAllById(userIds: Collection<String>): List<UserModel> = userRepo.findAllById(userIds)

    /** Null instead of a throw: the callers answer unknown emails like known ones to avoid an address oracle. */
    fun findByEmail(email: String): UserModel? = userRepo.findByEmail(email)

    fun existsByEmail(email: String): Boolean = userRepo.existsByEmail(email)

    fun listUsers(pageable: Pageable): Page<UserModel> = userRepo.findAll(pageable)

    fun listByCompany(companyId: String): List<UserModel> = userRepo.findAllByCompanyId(companyId)

    fun searchChatPartners(
        q: String,
        excludedId: String,
        pageable: Pageable,
    ): List<UserModel> = userRepo.searchChatPartners(q, excludedId, pageable)

    fun searchByRole(
        role: String,
        q: String,
        pageable: Pageable,
    ): Page<UserModel> = userRepo.searchByRole(role, q, pageable)

    fun save(user: UserModel): UserModel = userRepo.save(user)

    fun delete(user: UserModel) = userRepo.delete(user)
}
