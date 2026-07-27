package org.efehan.skillmatcherbackend.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.efehan.skillmatcherbackend.config.properties.CacheProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {
    companion object {
        const val SKILL_CATALOG = "skillCatalog"
        const val MATCHING_CANDIDATES = "matchingCandidates"
        const val MATCHING_PROJECTS_FOR_USER = "matchingProjectsForUser"
    }

    @Bean
    fun cacheManager(properties: CacheProperties): CacheManager =
        CaffeineCacheManager().apply {
            registerCustomCache(SKILL_CATALOG, buildCache(properties.skillCatalogTtl, properties.skillCatalogMaxSize))
            registerCustomCache(MATCHING_CANDIDATES, buildCache(properties.matchingTtl, properties.matchingMaxSize))
            registerCustomCache(MATCHING_PROJECTS_FOR_USER, buildCache(properties.matchingTtl, properties.matchingMaxSize))
        }

    private fun buildCache(
        ttl: Duration,
        maxSize: Long,
    ) = Caffeine
        .newBuilder()
        .expireAfterWrite(ttl)
        .maximumSize(maxSize)
        .build<Any, Any>()
}
