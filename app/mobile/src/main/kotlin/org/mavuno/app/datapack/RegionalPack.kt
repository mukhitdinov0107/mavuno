package org.mavuno.app.datapack

import android.content.Context
import org.mavuno.fusion.SiteContext

/** Rain and soil looked up for a plot. Outside the pack everything is unknown. */
data class SiteLookup(val insidePack: Boolean, val context: SiteContext)

/** Offline regional data pack (PRD 7.3), bundled under assets/content/datapack/. */
interface RegionalPack {
    val version: String

    fun lookup(lat: Double, lon: Double): SiteLookup

    companion object {
        const val MANIFEST_ASSET = "content/datapack/pack.json"

        // The pack reader arrives with datapack/ (build step 4); until then every plot is outside.
        @Suppress("UNUSED_PARAMETER")
        fun load(context: Context): RegionalPack = NoRegionalPack
    }
}

object NoRegionalPack : RegionalPack {
    override val version = "none"
    override fun lookup(lat: Double, lon: Double) = SiteLookup(insidePack = false, context = SiteContext())
}
