package org.efehan.skillmatcherbackend.shared.exceptions

import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.springframework.http.HttpStatus

data class AccountLockedException(
    val errorCode: GlobalErrorCode,
    val status: HttpStatus,
    override val message: String = "Account is temporarily locked",
) : RuntimeException(message)
