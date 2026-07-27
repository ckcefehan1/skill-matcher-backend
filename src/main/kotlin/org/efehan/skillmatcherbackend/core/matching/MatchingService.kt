package org.efehan.skillmatcherbackend.core.matching

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.config.properties.CapacityMode
import org.efehan.skillmatcherbackend.config.properties.MatchingProperties
import org.efehan.skillmatcherbackend.core.skill.SkillGraphService
import org.efehan.skillmatcherbackend.core.skill.SkillRelationInfo
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.ProjectSkillModel
import org.efehan.skillmatcherbackend.persistence.ProjectSkillRepository
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillPriority
import org.efehan.skillmatcherbackend.persistence.UserAvailabilityModel
import org.efehan.skillmatcherbackend.persistence.UserAvailabilityRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserSkillModel
import org.efehan.skillmatcherbackend.persistence.UserSkillRepository
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Service
@Transactional(readOnly = true)
class MatchingService(
    private val projectRepo: ProjectRepository,
    private val projectSkillRepo: ProjectSkillRepository,
    private val userSkillRepo: UserSkillRepository,
    private val availabilityRepo: UserAvailabilityRepository,
    private val projectMemberRepo: ProjectMemberRepository,
    private val matchingProperties: MatchingProperties,
    private val skillGraphService: SkillGraphService,
    private val applicationRepo: ProjectApplicationRepository,
) {
    companion object {
        // PM-side weights (candidate scoring) — coverage dominates
        const val PM_WEIGHT_MUST_HAVE = 0.45
        const val PM_WEIGHT_LEVEL_FIT = 0.25
        const val PM_WEIGHT_NICE_TO_HAVE = 0.10
        const val PM_WEIGHT_AVAILABILITY = 0.20

        // User-side weights (project scoring) — growth dominates
        const val USER_WEIGHT_MUST_HAVE = 0.25
        const val USER_WEIGHT_LEVEL_FIT = 0.15
        const val USER_WEIGHT_NICE_TO_HAVE = 0.10
        const val USER_WEIGHT_AVAILABILITY = 0.10
        const val USER_WEIGHT_GROWTH = 0.40

        const val LEVEL_OVERFIT_CAP = 1.2
        const val GROWTH_LEVEL_ZONE = 1

        const val APPLIED_BONUS = 0.05

        const val UNDERUTILIZATION_THRESHOLD = 2
        const val UNDERUTILIZATION_PENALTY = 0.2
    }

    // ponytail: application/member mutations (hasApplied bonus, capacity factor) evict nicht,
    // Ergebnisse dort bis zu cache.matching-ttl (60s) stale — bei Bedarf dort auch @CacheEvict
    @Cacheable(
        cacheNames = [CacheConfig.MATCHING_CANDIDATES],
        key = "{#projectId, #minScore, #limit, #tier}",
    )
    fun findCandidatesForProject(
        projectId: String,
        minScore: Double,
        limit: Int,
        tier: MatchTier = MatchTier.ALL,
    ): List<UserMatchDto> {
        val project = findProjectOrThrow(projectId)
        val projectSkills = projectSkillRepo.findByProject(project)

        if (projectSkills.isEmpty()) return emptyList()

        val requiredSkills = projectSkills.map { it.skill }
        val relationsMap = skillGraphService.expandSkills(requiredSkills)
        val expandedSkills = expandWithRelated(requiredSkills, relationsMap)

        val allUserSkills =
            userSkillRepo.findMatchableBySkillsForProject(
                skills = expandedSkills,
                project = project,
                activeStatus = ProjectMemberStatus.ACTIVE,
            )

        val userSkillMap = allUserSkills.groupBy { it.user.id }
        if (userSkillMap.isEmpty()) return emptyList()

        val candidateUsers = userSkillMap.values.map { it.first().user }
        val availabilityMap =
            availabilityRepo
                .findByUserIn(candidateUsers)
                .groupBy { it.user.id }

        val capacityMap =
            projectMemberRepo
                .countActiveByUserIn(candidateUsers, ProjectMemberStatus.ACTIVE)
                .associate { it.userId to it.count.toInt() }

        val appliedUserIds =
            applicationRepo
                .findByProjectAndUserInAndStatus(project, candidateUsers, ApplicationStatus.PENDING)
                .map { it.user.id }
                .toSet()

        return userSkillMap
            .asSequence()
            .map { (userId, skills) ->
                val user = skills.first().user
                val userAvailabilities = availabilityMap[userId].orEmpty()
                val activeProjects = capacityMap[userId] ?: 0
                computeCandidateMatch(
                    user,
                    skills,
                    projectSkills,
                    project,
                    userAvailabilities,
                    activeProjects,
                    relationsMap,
                    userId in appliedUserIds,
                )
            }.filter { matchesTier(it.matchTier, tier) }
            .filter { passesCapacityFilter(it) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    @Cacheable(
        cacheNames = [CacheConfig.MATCHING_PROJECTS_FOR_USER],
        key = "{#user.id, #minScore, #limit, #tier}",
    )
    fun findProjectsForUser(
        user: UserModel,
        minScore: Double,
        limit: Int,
        tier: MatchTier = MatchTier.ALL,
    ): List<ProjectMatchDto> {
        val userSkills = userSkillRepo.findByUser(user)
        if (userSkills.isEmpty()) return emptyList()

        val projects =
            projectRepo.findMatchableForUser(
                user = user,
                statuses = listOf(ProjectStatus.PLANNED, ProjectStatus.ACTIVE),
                activeStatus = ProjectMemberStatus.ACTIVE,
            )

        if (projects.isEmpty()) return emptyList()

        val projectSkillMap =
            projectSkillRepo
                .findByProjectIn(projects)
                .groupBy { it.project.id }

        val allProjectSkills =
            projectSkillMap.values
                .flatten()
                .map { it.skill }
                .distinct()
        val relationsMap = skillGraphService.expandSkills(allProjectSkills)

        val userAvailabilities = availabilityRepo.findByUser(user)

        val applicationStatusMap =
            applicationRepo
                .findByUserAndProjectIn(user, projects)
                .groupBy { it.project.id }
                .mapValues { (_, apps) -> apps.first().status.name }

        return projects
            .asSequence()
            .mapNotNull { project ->
                val projectSkills = projectSkillMap[project.id].orEmpty()
                if (projectSkills.isEmpty()) return@mapNotNull null
                computeProjectMatch(
                    user,
                    userSkills,
                    project,
                    projectSkills,
                    userAvailabilities,
                    relationsMap,
                    applicationStatusMap[project.id],
                )
            }.filter { matchesTier(it.matchTier, tier) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    private fun expandWithRelated(
        required: List<SkillModel>,
        relationsMap: Map<String, List<SkillRelationInfo>>,
    ): List<SkillModel> {
        val related = relationsMap.values.flatten().map { it.relatedSkill }
        return (required + related).distinctBy { it.id }
    }

    private fun computeCandidateMatch(
        user: UserModel,
        userSkills: List<UserSkillModel>,
        projectSkills: List<ProjectSkillModel>,
        project: ProjectModel,
        availabilities: List<UserAvailabilityModel>,
        activeProjects: Int,
        relationsMap: Map<String, List<SkillRelationInfo>>,
        hasApplied: Boolean,
    ): UserMatchDto {
        val components = computeScoreComponents(userSkills, projectSkills, project, availabilities, ScoreMode.CANDIDATE, relationsMap)
        val tier = determineTier(components.mustHaveCoverage)

        val rawScore =
            PM_WEIGHT_MUST_HAVE * components.mustHaveCoverage +
                PM_WEIGHT_LEVEL_FIT * components.levelFitScore +
                PM_WEIGHT_NICE_TO_HAVE * components.niceToHaveCoverage +
                PM_WEIGHT_AVAILABILITY * components.availabilityScore

        val capacityFactor = computeCapacityFactor(activeProjects, user.maxConcurrentProjects)
        val bonus = if (hasApplied) APPLIED_BONUS else 0.0
        val score = roundToTwoDecimals(min(1.0, rawScore * capacityFactor + bonus))

        return UserMatchDto(
            userId = user.id,
            userName = "${user.firstName} ${user.lastName}",
            email = user.email,
            score = score,
            matchTier = tier.name,
            capacityLoad = activeProjects,
            capacityMax = user.maxConcurrentProjects,
            hasApplied = hasApplied,
            breakdown =
                MatchScoreBreakdown(
                    mustHaveCoverage = roundToTwoDecimals(components.mustHaveCoverage),
                    levelFitScore = roundToTwoDecimals(components.levelFitScore),
                    niceToHaveCoverage = roundToTwoDecimals(components.niceToHaveCoverage),
                    availabilityScore = roundToTwoDecimals(components.availabilityScore),
                ),
            matchedSkills = components.matchedSkills,
            missingSkills = components.missingSkills,
        )
    }

    private fun computeProjectMatch(
        user: UserModel,
        userSkills: List<UserSkillModel>,
        project: ProjectModel,
        projectSkills: List<ProjectSkillModel>,
        availabilities: List<UserAvailabilityModel>,
        relationsMap: Map<String, List<SkillRelationInfo>>,
        applicationStatus: String?,
    ): ProjectMatchDto {
        val components = computeScoreComponents(userSkills, projectSkills, project, availabilities, ScoreMode.PROJECT, relationsMap)
        val tier = determineTier(components.mustHaveCoverage)

        val rawScore =
            USER_WEIGHT_MUST_HAVE * components.mustHaveCoverage +
                USER_WEIGHT_LEVEL_FIT * components.levelFitScore +
                USER_WEIGHT_NICE_TO_HAVE * components.niceToHaveCoverage +
                USER_WEIGHT_AVAILABILITY * components.availabilityScore +
                USER_WEIGHT_GROWTH * components.growthPotential

        val score = roundToTwoDecimals(rawScore * components.underutilizationFactor)

        return ProjectMatchDto(
            projectId = project.id,
            projectName = project.name,
            projectDescription = project.description,
            status = project.status.name,
            ownerName = "${project.owner.firstName} ${project.owner.lastName}",
            score = score,
            matchTier = tier.name,
            growthPotential = roundToTwoDecimals(components.growthPotential),
            applicationStatus = applicationStatus,
            breakdown =
                MatchScoreBreakdown(
                    mustHaveCoverage = roundToTwoDecimals(components.mustHaveCoverage),
                    levelFitScore = roundToTwoDecimals(components.levelFitScore),
                    niceToHaveCoverage = roundToTwoDecimals(components.niceToHaveCoverage),
                    availabilityScore = roundToTwoDecimals(components.availabilityScore),
                ),
            matchedSkills = components.matchedSkills,
            missingSkills = components.missingSkills,
        )
    }

    private enum class ScoreMode { CANDIDATE, PROJECT }

    private data class SkillMatchResult(
        val userSkill: UserSkillModel,
        val penalty: Double,
        val matchedVia: String?,
    )

    private data class ScoreComponents(
        val mustHaveCoverage: Double,
        val levelFitScore: Double,
        val niceToHaveCoverage: Double,
        val availabilityScore: Double,
        val growthPotential: Double,
        val underutilizationFactor: Double,
        val matchedSkills: List<MatchedSkillDto>,
        val missingSkills: List<MissingSkillDto>,
    )

    private fun computeScoreComponents(
        userSkills: List<UserSkillModel>,
        projectSkills: List<ProjectSkillModel>,
        project: ProjectModel,
        availabilities: List<UserAvailabilityModel>,
        mode: ScoreMode,
        relationsMap: Map<String, List<SkillRelationInfo>>,
    ): ScoreComponents {
        val userSkillMap = userSkills.associateBy { it.skill.id }

        val matchedSkills = mutableListOf<MatchedSkillDto>()
        val missingSkills = mutableListOf<MissingSkillDto>()

        var mustHaveFulfilled = 0
        var niceToHaveWeighted = 0.0
        val levelFitSamples = mutableListOf<Triple<Int, Int, Double>>()

        projectSkills.forEach { ps ->
            val match = findBestSkillMatch(ps, userSkillMap, relationsMap)
            if (match == null) {
                missingSkills.add(
                    MissingSkillDto(
                        skillId = ps.skill.id,
                        skillName = ps.skill.name,
                        requiredLevel = ps.level,
                        priority = ps.priority.name,
                    ),
                )
                return@forEach
            }

            matchedSkills.add(
                MatchedSkillDto(
                    skillId = ps.skill.id,
                    skillName = ps.skill.name,
                    userLevel = match.userSkill.level,
                    requiredLevel = ps.level,
                    priority = ps.priority.name,
                    matchedVia = match.matchedVia,
                ),
            )
            levelFitSamples.add(Triple(match.userSkill.level, ps.level, match.penalty))

            val isDirect = match.matchedVia == null
            when (ps.priority) {
                SkillPriority.MUST_HAVE -> if (isDirect && match.userSkill.level >= ps.level) mustHaveFulfilled++
                SkillPriority.NICE_TO_HAVE -> niceToHaveWeighted += match.penalty
            }
        }

        val mustHaveCount = projectSkills.count { it.priority == SkillPriority.MUST_HAVE }
        val niceToHaveCount = projectSkills.count { it.priority == SkillPriority.NICE_TO_HAVE }
        val mustHaveCoverage = if (mustHaveCount == 0) 1.0 else mustHaveFulfilled.toDouble() / mustHaveCount
        val niceToHaveCoverage = if (niceToHaveCount == 0) 1.0 else niceToHaveWeighted / niceToHaveCount

        val levelFitScore = computeLevelFit(levelFitSamples, mode)
        val growthPotential = computeGrowthPotential(projectSkills, userSkillMap)
        val availabilityScore = computeAvailabilityScore(availabilities, project)
        val underutilizationFactor =
            if (mode == ScoreMode.PROJECT) {
                computeUnderutilizationFactor(projectSkills, userSkillMap)
            } else {
                1.0
            }

        return ScoreComponents(
            mustHaveCoverage = mustHaveCoverage,
            levelFitScore = levelFitScore,
            niceToHaveCoverage = niceToHaveCoverage,
            availabilityScore = availabilityScore,
            growthPotential = growthPotential,
            underutilizationFactor = underutilizationFactor,
            matchedSkills = matchedSkills,
            missingSkills = missingSkills,
        )
    }

    private fun findBestSkillMatch(
        projectSkill: ProjectSkillModel,
        userSkillMap: Map<String, UserSkillModel>,
        relationsMap: Map<String, List<SkillRelationInfo>>,
    ): SkillMatchResult? {
        userSkillMap[projectSkill.skill.id]?.let {
            return SkillMatchResult(it, 1.0, null)
        }
        val relations = relationsMap[projectSkill.skill.id].orEmpty()
        var best: SkillMatchResult? = null
        for (rel in relations) {
            val us = userSkillMap[rel.relatedSkill.id] ?: continue
            if (best == null || rel.transferPenalty > best.penalty) {
                best = SkillMatchResult(us, rel.transferPenalty, rel.relatedSkill.name)
            }
        }
        return best
    }

    private fun computeLevelFit(
        samples: List<Triple<Int, Int, Double>>,
        mode: ScoreMode,
    ): Double {
        if (samples.isEmpty()) return 0.0
        return samples.sumOf { (userLevel, reqLevel, penalty) ->
            val ratio = userLevel.toDouble() / reqLevel.toDouble()
            val base =
                when (mode) {
                    ScoreMode.CANDIDATE -> if (ratio >= 1.0) 1.0 else ratio.pow(1.5)
                    ScoreMode.PROJECT -> min(ratio, LEVEL_OVERFIT_CAP) / LEVEL_OVERFIT_CAP
                }
            penalty * base
        } / samples.size
    }

    private fun computeGrowthPotential(
        projectSkills: List<ProjectSkillModel>,
        userSkillMap: Map<String, UserSkillModel>,
    ): Double {
        if (projectSkills.isEmpty()) return 0.0
        val inZone =
            projectSkills.count { ps ->
                val us = userSkillMap[ps.skill.id]
                us != null && abs(us.level - ps.level) <= GROWTH_LEVEL_ZONE
            }
        return inZone.toDouble() / projectSkills.size
    }

    private fun computeUnderutilizationFactor(
        projectSkills: List<ProjectSkillModel>,
        userSkillMap: Map<String, UserSkillModel>,
    ): Double {
        if (projectSkills.isEmpty()) return 1.0
        val matched = projectSkills.mapNotNull { ps -> userSkillMap[ps.skill.id]?.let { ps to it } }
        if (matched.isEmpty()) return 1.0
        val underutilized =
            matched.count { (ps, us) ->
                us.level > ps.level + UNDERUTILIZATION_THRESHOLD
            }
        if (underutilized == 0) return 1.0
        val ratio = underutilized.toDouble() / matched.size
        return (1.0 - UNDERUTILIZATION_PENALTY * ratio).coerceIn(0.0, 1.0)
    }

    private fun determineTier(mustHaveCoverage: Double): MatchTier =
        when {
            mustHaveCoverage >= 1.0 -> MatchTier.EXACT
            mustHaveCoverage >= matchingProperties.mustHaveCoverageThreshold -> MatchTier.FALLBACK
            else -> MatchTier.STRETCH
        }

    private fun matchesTier(
        candidateTier: String,
        filter: MatchTier,
    ): Boolean = filter == MatchTier.ALL || candidateTier == filter.name

    private fun computeCapacityFactor(
        activeProjects: Int,
        maxProjects: Int,
    ): Double {
        if (activeProjects < maxProjects) return 1.0
        return when (matchingProperties.capacityMode) {
            CapacityMode.HARD -> 0.0
            CapacityMode.SOFT -> 1.0 - matchingProperties.capacityPenalty
        }
    }

    private fun passesCapacityFilter(match: UserMatchDto): Boolean =
        matchingProperties.capacityMode != CapacityMode.HARD || match.capacityLoad < match.capacityMax

    private fun computeAvailabilityScore(
        availabilities: List<UserAvailabilityModel>,
        project: ProjectModel,
    ): Double {
        if (availabilities.isEmpty()) return 1.0

        val projectStart = project.startDate
        val projectEnd = project.endDate
        val projectDays = ChronoUnit.DAYS.between(projectStart, projectEnd)
        if (projectDays <= 0) return 1.0

        val coveredDays =
            availabilities.sumOf { avail ->
                val overlapStart = maxOf(projectStart, avail.availableFrom)
                val overlapEnd = minOf(projectEnd, avail.availableTo)
                val overlap = ChronoUnit.DAYS.between(overlapStart, overlapEnd)
                if (overlap > 0) overlap else 0L
            }

        return min(coveredDays.toDouble() / projectDays.toDouble(), 1.0)
    }

    private fun findProjectOrThrow(projectId: String): ProjectModel =
        projectRepo
            .findById(projectId)
            .orElseThrow {
                EntryNotFoundException(
                    resource = "Project",
                    field = "id",
                    value = projectId,
                    errorCode = GlobalErrorCode.PROJECT_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }

    private fun roundToTwoDecimals(value: Double): Double = (value * 100.0).roundToInt() / 100.0
}
