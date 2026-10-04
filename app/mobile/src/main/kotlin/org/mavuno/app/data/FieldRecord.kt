package org.mavuno.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Field record schema from PRD 7.6. This is exactly what is stored and, with consent, sent. */
@Serializable
data class FieldRecord(
    @SerialName("record_id") val recordId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("model_version") val modelVersion: String,
    @SerialName("pack_version") val packVersion: String,
    @SerialName("fusion_version") val fusionVersion: String,
    val language: String,
    val farmer: Farmer,
    val plot: PlotInfo,
    val photos: List<PhotoInfo>,
    val interview: Map<String, String>,
    val context: ContextInfo,
    val result: ResultInfo,
    val consent: Consent,
) {
    @Serializable
    data class Farmer(
        @SerialName("farmer_id") val farmerId: String,
        val name: String,
        val phone: String,
        val cooperative: String,
    )

    @Serializable
    data class PlotInfo(
        @SerialName("plot_id") val plotId: String,
        val lat: Double,
        val lon: Double,
        val crop: String = "coffee_arabica",
    )

    @Serializable
    data class PhotoInfo(
        @SerialName("photo_id") val photoId: String,
        @SerialName("tree") val treeIndex: Int,
        @SerialName("class") val leafClass: String?,
        val prob: Double?,
        val accepted: Boolean,
        /** Only filled in the upload payload, and only with share_photos consent. */
        @SerialName("jpeg_base64") val jpegBase64: String? = null,
    )

    @Serializable
    data class ContextInfo(
        @SerialName("inside_pack") val insidePack: Boolean,
        @SerialName("rain_flowering_pct") val rainFloweringPct: Int? = null,
        @SerialName("rain_berry_pct") val rainBerryPct: Int? = null,
        @SerialName("soil_ph") val soilPh: Double? = null,
        @SerialName("soil_n_g_per_kg") val soilN: Double? = null,
        @SerialName("soil_k_mg_per_kg") val soilK: Double? = null,
        @SerialName("data_through") val dataThrough: String? = null,
    )

    @Serializable
    data class ResultInfo(
        val status: String,
        val causes: List<CauseInfo>,
        @SerialName("abstain_reasons") val abstainReasons: List<String>,
        @SerialName("known_features") val knownFeatures: Int,
    )

    @Serializable
    data class CauseInfo(
        val id: String,
        val prob: Double,
        val label: String,
        val reasons: List<ReasonInfo>,
    )

    @Serializable
    data class ReasonInfo(val id: String, val params: Map<String, String> = emptyMap())

    @Serializable
    data class Consent(
        @SerialName("share_record") val shareRecord: Boolean,
        @SerialName("share_photos") val sharePhotos: Boolean,
    )

    companion object {
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    }
}
