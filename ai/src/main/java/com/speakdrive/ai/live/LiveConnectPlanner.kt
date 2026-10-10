package com.speakdrive.ai.live

/**
 * Orders the connection attempts of one connect and prunes what a failure rules out. Kept free of
 * the Firebase SDK so it can be tested.
 *
 * Models are tried best first. For each, the full tool set, then fewer tools (the preview Live API
 * may refuse large tool declarations). For each of those: resuming the lesson's server-side session
 * when a handle exists, then a fresh session with resumption on, then, while it is not yet known
 * whether the server accepts resumption at all, one with it off.
 */
class LiveConnectPlanner(
    private val models: List<String>,
    private val toolSetCount: Int,
    private val hasHandle: (model: String) -> Boolean,
    private val resumption: ResumptionSupport,
    private val maxAttempts: Int = MAX_ATTEMPTS
) {
    enum class Resume { HANDLE, FRESH, OFF }

    enum class Failure {
        /** No other attempt can help (no network, refused credentials, a timeout on the last model). */
        FATAL,

        /** This model is not available to us; try the next one. */
        MODEL_REFUSED,

        /** Something about this attempt; a smaller one may work. */
        OTHER
    }

    /** What is known about the server accepting session resumption. */
    enum class ResumptionSupport { UNKNOWN, CONFIRMED, UNSUPPORTED }

    data class Attempt(val model: String, val toolSet: Int, val resume: Resume)

    private var modelIndex = 0
    private var toolSet = 0
    private var options: List<Resume>? = null
    private var optionIndex = 0
    private var attempts = 0
    private var handleRejected = false
    private var freshFailed = false
    private val refusedModels = mutableSetOf<String>()

    fun next(): Attempt? {
        while (modelIndex < models.size && attempts < maxAttempts) {
            val model = models[modelIndex]
            if (model in refusedModels || toolSet >= toolSetCount) {
                nextModel()
                continue
            }
            val current = options ?: resumeOptions(model).also { options = it }
            if (optionIndex >= current.size) {
                toolSet++
                options = null
                optionIndex = 0
                continue
            }
            attempts++
            return Attempt(model, toolSet, current[optionIndex++])
        }
        return null
    }

    fun failed(attempt: Attempt, failure: Failure) {
        when (failure) {
            Failure.FATAL -> modelIndex = models.size
            Failure.MODEL_REFUSED -> refusedModels += attempt.model
            Failure.OTHER -> when (attempt.resume) {
                Resume.HANDLE -> handleRejected = true
                Resume.FRESH -> freshFailed = true
                Resume.OFF -> Unit
            }
        }
    }

    /** What a successful [attempt] teaches about session resumption on this server. */
    fun resumptionAfter(attempt: Attempt): ResumptionSupport = when {
        attempt.resume != Resume.OFF -> ResumptionSupport.CONFIRMED
        freshFailed -> ResumptionSupport.UNSUPPORTED
        else -> resumption
    }

    private fun nextModel() {
        modelIndex++
        toolSet = 0
        options = null
        optionIndex = 0
    }

    private fun resumeOptions(model: String): List<Resume> = buildList {
        if (!handleRejected && resumption != ResumptionSupport.UNSUPPORTED && hasHandle(model)) add(Resume.HANDLE)
        if (resumption != ResumptionSupport.UNSUPPORTED) add(Resume.FRESH)
        if (resumption != ResumptionSupport.CONFIRMED) add(Resume.OFF)
    }

    companion object {
        /** Each failed attempt is quick, but a learner should not wait through a dozen of them. */
        const val MAX_ATTEMPTS = 8

        private val AUTH_REFUSALS = listOf(
            "app check", "appcheck", "attestation", "too many attempts", "unauthenticated",
            "permission", "api key", "api_key", "service_blocked", "service_disabled", "403"
        )
        private val MODEL_REFUSALS = listOf(
            "not found", "not supported", "unsupported model", "invalid model", "unknown model",
            "model is not available", "does not exist", "404"
        )

        /**
         * How bad a connection failure is. On an earlier model a timeout or a "not found / not
         * supported" most likely means that model is not available to us, so the next one is tried.
         * On the last model a timeout ends the connect and anything else may still work with fewer tools.
         */
        fun classify(e: Throwable, lastModel: Boolean): Failure {
            if (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.NoRouteToHostException) {
                return Failure.FATAL
            }
            val text = (e.message.orEmpty() + " " + e.cause?.message.orEmpty()).lowercase()
            return when {
                AUTH_REFUSALS.any { it in text } -> Failure.FATAL
                "timed out" in text || "timeout" in text -> if (lastModel) Failure.FATAL else Failure.MODEL_REFUSED
                MODEL_REFUSALS.any { it in text } -> if (lastModel) Failure.OTHER else Failure.MODEL_REFUSED
                else -> Failure.OTHER
            }
        }
    }
}
