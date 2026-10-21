package com.remotepool

import com.remotepool.exceptions.InvalidDsnException
import com.remotepool.models.AuthUser

/**
 * Connection string of a remote pool:
 *
 *     selectel;<projectName>;<userUID>;<username>;<userPassword>
 *
 * `userUID` is the account (domain) id. The password is the last field and may itself contain ';'.
 */
class PoolDsn private constructor(
    val provider: String,
    val user: AuthUser,
    val password: String,
) {
    // never print the password (logs, crash reports)
    override fun toString() = "PoolDsn(provider=$provider, user=$user, password=***)"

    companion object {
        const val PROVIDER_SELECTEL = "selectel"
        private const val FIELDS = 5

        /** @throws com.remotepool.exceptions.InvalidDsnException */
        fun parse(dsn: String): PoolDsn {
            val parts = dsn.trim().split(';', limit = FIELDS)
            if (parts.size != FIELDS) throw InvalidDsnException(InvalidDsnException.Reason.FORMAT)
            val provider = parts[0].trim()
            val project = parts[1].trim()
            val uid = parts[2].trim()
            val username = parts[3].trim()
            val password = parts[4] // as typed: it may legitimately contain spaces and ';'
            if (!provider.equals(PROVIDER_SELECTEL, ignoreCase = true))
                throw InvalidDsnException(InvalidDsnException.Reason.PROVIDER, provider)
            if (project.isEmpty() || uid.isEmpty() || username.isEmpty() || password.isEmpty())
                throw InvalidDsnException(InvalidDsnException.Reason.EMPTY_FIELD)
            return PoolDsn(PROVIDER_SELECTEL, AuthUser(username = username, accountId = uid, projectName = project), password)
        }
    }
}
