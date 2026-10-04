package org.mavuno.app

import android.content.Context
import java.util.UUID

data class FarmerProfile(val farmerId: String, val name: String, val phone: String, val cooperative: String)

data class Plot(val plotId: String, val lat: Double, val lon: Double)

/**
 * Small settings. The farmer's name, phone and plot live here (app-private storage, excluded from backup);
 * records and photos live in the encrypted database.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mavuno", Context.MODE_PRIVATE)

    var language: String?
        get() = sp.getString("language", null)
        set(value) = sp.edit().putString("language", value).apply()

    var profile: FarmerProfile?
        get() {
            val id = sp.getString("farmer_id", null) ?: return null
            return FarmerProfile(id, sp.getString("name", "")!!, sp.getString("phone", "")!!, sp.getString("cooperative", "")!!)
        }
        set(value) {
            sp.edit().apply {
                if (value == null) {
                    remove("farmer_id"); remove("name"); remove("phone"); remove("cooperative")
                } else {
                    putString("farmer_id", value.farmerId); putString("name", value.name)
                    putString("phone", value.phone); putString("cooperative", value.cooperative)
                }
            }.apply()
        }

    var plot: Plot?
        get() {
            val id = sp.getString("plot_id", null) ?: return null
            return Plot(id, sp.getString("lat", "0")!!.toDouble(), sp.getString("lon", "0")!!.toDouble())
        }
        set(value) {
            sp.edit().apply {
                if (value == null) {
                    remove("plot_id"); remove("lat"); remove("lon")
                } else {
                    putString("plot_id", value.plotId); putString("lat", value.lat.toString()); putString("lon", value.lon.toString())
                }
            }.apply()
        }

    /** Wrapped database passphrase (see DbKey). */
    var wrappedDbKey: String?
        get() = sp.getString("db_key", null)
        set(value) = sp.edit().putString("db_key", value).apply()

    val isSetUp: Boolean get() = language != null && profile != null && plot != null

    fun clear() = sp.edit().clear().apply()

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
