package com.speakdrive.data.scenario

import com.speakdrive.ai.model.CustomScenario
import com.speakdrive.ai.model.Scenario
import com.speakdrive.data.local.dao.ScenarioDao
import com.speakdrive.data.local.entity.CustomScenarioEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomScenarioManager @Inject constructor(
    private val scenarioDao: ScenarioDao
) {

    fun observeCustomScenarios(): Flow<List<CustomScenario>> =
        scenarioDao.observeAll().map { entities ->
            entities.map { it.toModel() }
        }

    suspend fun getCustomScenarios(): List<CustomScenario> =
        scenarioDao.getAll().map { it.toModel() }

    suspend fun createScenario(
        titleVi: String,
        titleEn: String,
        aiRole: String,
        learnerRole: String,
        customContext: String,
        missionObjective: String? = null
    ): CustomScenario {
        val scenario = CustomScenario(
            id = "custom_${UUID.randomUUID()}",
            titleVi = titleVi,
            titleEn = titleEn,
            aiRole = aiRole,
            learnerRole = learnerRole,
            customContext = customContext,
            missionObjective = missionObjective
        )
        scenarioDao.insert(scenario.toEntity())
        return scenario
    }

    suspend fun deleteScenario(id: String) {
        scenarioDao.deleteById(id)
    }

    private fun CustomScenarioEntity.toModel(): CustomScenario = CustomScenario(
        id = id,
        titleVi = titleVi,
        titleEn = titleEn,
        aiRole = aiRole,
        learnerRole = learnerRole,
        customContext = customContext,
        missionObjective = missionObjective,
        createdAt = createdAt
    )

    private fun CustomScenario.toEntity(): CustomScenarioEntity = CustomScenarioEntity(
        id = id,
        titleVi = titleVi,
        titleEn = titleEn,
        aiRole = aiRole,
        learnerRole = learnerRole,
        customContext = customContext,
        missionObjective = missionObjective,
        createdAt = createdAt
    )
}
