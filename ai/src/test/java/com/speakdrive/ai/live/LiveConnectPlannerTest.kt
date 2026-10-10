package com.speakdrive.ai.live

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.live.LiveConnectPlanner.Attempt
import com.speakdrive.ai.live.LiveConnectPlanner.Failure
import com.speakdrive.ai.live.LiveConnectPlanner.Resume
import com.speakdrive.ai.live.LiveConnectPlanner.ResumptionSupport
import org.junit.Test

class LiveConnectPlannerTest {

    private fun planner(
        models: List<String> = listOf("new", "old"),
        toolSets: Int = 3,
        handleFor: String? = null,
        resumption: ResumptionSupport = ResumptionSupport.UNKNOWN,
        maxAttempts: Int = 20
    ) = LiveConnectPlanner(models, toolSets, { it == handleFor }, resumption, maxAttempts)

    /** Runs the plan, failing every attempt with [failure] until [succeedAt] (if any). */
    private fun LiveConnectPlanner.run(failure: (Attempt) -> Failure, succeedAt: (Attempt) -> Boolean = { false }): List<Attempt> {
        val tried = mutableListOf<Attempt>()
        while (true) {
            val attempt = next() ?: return tried
            tried += attempt
            if (succeedAt(attempt)) return tried
            failed(attempt, failure(attempt))
        }
    }

    @Test
    fun `first connect tries resumption on and off for each tool set, then the next model`() {
        val tried = planner(models = listOf("new", "old"), toolSets = 2).run({ Failure.OTHER })

        assertThat(tried).containsExactly(
            Attempt("new", 0, Resume.FRESH), Attempt("new", 0, Resume.OFF),
            Attempt("new", 1, Resume.FRESH), Attempt("new", 1, Resume.OFF),
            Attempt("old", 0, Resume.FRESH), Attempt("old", 0, Resume.OFF),
            Attempt("old", 1, Resume.FRESH), Attempt("old", 1, Resume.OFF)
        ).inOrder()
    }

    @Test
    fun `a refused model is skipped at once`() {
        val tried = planner().run({ if (it.model == "new") Failure.MODEL_REFUSED else Failure.OTHER }) { it.model == "old" }

        assertThat(tried).containsExactly(Attempt("new", 0, Resume.FRESH), Attempt("old", 0, Resume.FRESH)).inOrder()
    }

    @Test
    fun `a fatal failure ends the plan`() {
        val tried = planner().run({ Failure.FATAL })

        assertThat(tried).containsExactly(Attempt("new", 0, Resume.FRESH))
    }

    @Test
    fun `the lesson's handle is tried first and dropped once rejected`() {
        val tried = planner(handleFor = "new", resumption = ResumptionSupport.CONFIRMED).run({ Failure.OTHER })

        assertThat(tried.take(3)).containsExactly(
            Attempt("new", 0, Resume.HANDLE), Attempt("new", 0, Resume.FRESH), Attempt("new", 1, Resume.FRESH)
        ).inOrder()
        assertThat(tried.count { it.resume == Resume.HANDLE }).isEqualTo(1)
        assertThat(tried.none { it.resume == Resume.OFF }).isTrue()
    }

    @Test
    fun `a handle of another model is not used`() {
        val tried = planner(handleFor = "old", resumption = ResumptionSupport.CONFIRMED).run({ Failure.OTHER }) { true }

        assertThat(tried).containsExactly(Attempt("new", 0, Resume.FRESH))
    }

    @Test
    fun `success teaches whether the server accepts resumption`() {
        val fresh = planner()
        val first = fresh.next()!!
        assertThat(fresh.resumptionAfter(first)).isEqualTo(ResumptionSupport.CONFIRMED)

        val refused = planner()
        refused.failed(refused.next()!!, Failure.OTHER)
        val off = refused.next()!!
        assertThat(off.resume).isEqualTo(Resume.OFF)
        assertThat(refused.resumptionAfter(off)).isEqualTo(ResumptionSupport.UNSUPPORTED)

        val unsupported = planner(resumption = ResumptionSupport.UNSUPPORTED, handleFor = "new")
        assertThat(unsupported.next()).isEqualTo(Attempt("new", 0, Resume.OFF))
    }

    @Test
    fun `attempts are capped`() {
        val tried = planner(maxAttempts = 3).run({ Failure.OTHER })

        assertThat(tried).hasSize(3)
    }

    @Test
    fun `failures are classified by what can still help`() {
        assertThat(LiveConnectPlanner.classify(java.net.UnknownHostException("x"), lastModel = false)).isEqualTo(Failure.FATAL)
        assertThat(LiveConnectPlanner.classify(IllegalStateException("App Check token rejected"), lastModel = false))
            .isEqualTo(Failure.FATAL)
        assertThat(LiveConnectPlanner.classify(IllegalStateException("Live connection timed out after 15 s"), lastModel = false))
            .isEqualTo(Failure.MODEL_REFUSED)
        assertThat(LiveConnectPlanner.classify(IllegalStateException("Live connection timed out after 15 s"), lastModel = true))
            .isEqualTo(Failure.FATAL)
        val notFound = IllegalStateException(
            "Channel was closed by the server. Details: models/gemini-3.8-live is not found for API version v1beta"
        )
        assertThat(LiveConnectPlanner.classify(notFound, lastModel = false)).isEqualTo(Failure.MODEL_REFUSED)
        assertThat(LiveConnectPlanner.classify(notFound, lastModel = true)).isEqualTo(Failure.OTHER)
        assertThat(LiveConnectPlanner.classify(IllegalStateException("Invalid argument in tools"), lastModel = false))
            .isEqualTo(Failure.OTHER)
    }
}
