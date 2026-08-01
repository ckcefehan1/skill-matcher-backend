package org.efehan.skillmatcherbackend.core.tenant

/**
 * Root context (null) means no tenant filter — login, token flows, SUPERADMIN,
 * background threads. Every service path outside JwtAuthenticationFilter must
 * set this explicitly, otherwise it sees all tenants.
 */
object TenantContext {
    private val current = ThreadLocal<String?>()

    fun set(companyId: String) = current.set(companyId)

    fun get(): String? = current.get()

    fun clear() = current.remove()
}
