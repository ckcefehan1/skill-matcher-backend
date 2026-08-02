package org.efehan.skillmatcherbackend.shared.exceptions

import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.springframework.http.HttpStatus

data class ConflictException(
    val resource: String,
    val errorCode: GlobalErrorCode,
    val status: HttpStatus,
    override val message: String = "Conflict on $resource.",
) : RuntimeException(message)
