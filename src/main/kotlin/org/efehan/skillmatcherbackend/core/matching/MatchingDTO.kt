package org.efehan.skillmatcherbackend.core.matching

import io.swagger.v3.oas.annotations.media.Schema

enum class MatchTier {
    EXACT,
    FALLBACK,
    STRETCH,
    ALL,
}

data class MatchScoreBreakdown(
    @Schema(example = "1.0", description = "Anteil der erfüllten MUST_HAVE Skills (0.0–1.0)")
    val mustHaveCoverage: Double,
    @Schema(example = "0.92", description = "Wie gut die Skill-Level passen (0.0–1.0)")
    val levelFitScore: Double,
    @Schema(example = "0.5", description = "Anteil der vorhandenen NICE_TO_HAVE Skills (0.0–1.0)")
    val niceToHaveCoverage: Double,
    @Schema(example = "1.0", description = "Zeitliche Verfügbarkeit im Projektzeitraum (0.0–1.0)")
    val availabilityScore: Double,
)

data class MatchedSkillDto(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val skillId: String,
    @Schema(example = "kotlin")
    val skillName: String,
    @Schema(example = "4")
    val userLevel: Int,
    @Schema(example = "3")
    val requiredLevel: Int,
    @Schema(example = "MUST_HAVE")
    val priority: String,
    @Schema(example = "null", description = "Name of the related skill used for this match, or null for a direct skill match")
    val matchedVia: String? = null,
)

data class MissingSkillDto(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440001")
    val skillId: String,
    @Schema(example = "docker")
    val skillName: String,
    @Schema(example = "2")
    val requiredLevel: Int,
    @Schema(example = "NICE_TO_HAVE")
    val priority: String,
)

data class UserMatchDto(
    val userId: String,
    @Schema(example = "Max Mustermann")
    val userName: String,
    @Schema(example = "max@firma.de")
    val email: String,
    @Schema(example = "0.87")
    val score: Double,
    @Schema(example = "EXACT", description = "Match-Tier: EXACT (100% Must-have), FALLBACK (≥ Threshold), STRETCH (< Threshold)")
    val matchTier: String,
    @Schema(example = "1", description = "Anzahl aktiver Projekte des Users")
    val capacityLoad: Int,
    @Schema(example = "3", description = "Maximale parallele Projekte des Users")
    val capacityMax: Int,
    @Schema(example = "false", description = "Ob der User eine aktive (PENDING) Bewerbung für dieses Projekt hat")
    val hasApplied: Boolean,
    val breakdown: MatchScoreBreakdown,
    val matchedSkills: List<MatchedSkillDto>,
    val missingSkills: List<MissingSkillDto>,
)

data class ProjectMatchDto(
    val projectId: String,
    @Schema(example = "Skill Matcher")
    val projectName: String,
    @Schema(example = "Internal tool to match employees to projects based on skills.")
    val projectDescription: String,
    @Schema(example = "PLANNED")
    val status: String,
    @Schema(example = "Max Mustermann")
    val ownerName: String,
    @Schema(example = "0.87")
    val score: Double,
    @Schema(example = "EXACT", description = "Match-Tier aus User-Sicht")
    val matchTier: String,
    @Schema(example = "0.8", description = "Lernpotenzial: Anteil der Skills im ±1 Level-Bereich (0.0–1.0)")
    val growthPotential: Double,
    @Schema(example = "PENDING", description = "Status der eigenen Bewerbung für dieses Projekt, oder null wenn nicht beworben")
    val applicationStatus: String?,
    val breakdown: MatchScoreBreakdown,
    val matchedSkills: List<MatchedSkillDto>,
    val missingSkills: List<MissingSkillDto>,
)
