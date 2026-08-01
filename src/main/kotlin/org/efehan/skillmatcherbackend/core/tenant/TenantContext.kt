package org.efehan.skillmatcherbackend.core.tenant

/**
 * Fail-closed: an empty context is NOT root. Hibernate refuses tenant-scoped access
 * unless either a tenant is set or the path has explicitly declared itself root via
 * [runAsRoot]/[allowRoot] (login, token flows, SUPERADMIN, background bootstrap).
 */
object TenantContext {
    private val current = ThreadLocal<String?>()
    private val rootExplicit = ThreadLocal<Boolean>()

    fun set(companyId: String) = current.set(companyId)

    // static so the @Cacheable key expressions can reach it via SpEL's T(...) operator
    @JvmStatic
    fun get(): String? = current.get()

    fun isRootExplicit(): Boolean = rootExplicit.get() == true

    /** One-shot root marking for interceptor pairs (beforeHandle/afterMessageHandled). */
    fun allowRoot() = rootExplicit.set(true)

    fun clear() {
        current.remove()
        rootExplicit.remove()
    }

    fun <T> runAsRoot(block: () -> T): T {
        val previousTenant = current.get()
        val previousRoot = isRootExplicit()
        current.remove()
        rootExplicit.set(true)
        try {
            return block()
        } finally {
            restore(previousTenant, previousRoot)
        }
    }

    fun <T> withTenant(
        companyId: String,
        block: () -> T,
    ): T {
        val previousTenant = current.get()
        val previousRoot = isRootExplicit()
        current.set(companyId)
        rootExplicit.remove()
        try {
            return block()
        } finally {
            restore(previousTenant, previousRoot)
        }
    }

    private fun restore(
        tenant: String?,
        root: Boolean,
    ) {
        tenant?.let { current.set(it) } ?: current.remove()
        if (root) rootExplicit.set(true) else rootExplicit.remove()
    }
}
