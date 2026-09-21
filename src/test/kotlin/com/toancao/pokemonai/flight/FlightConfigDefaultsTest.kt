package com.toancao.pokemonai.flight

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Đợt 3: bảo vệ tương thích config bay.
 * FlightConfig không phụ thuộc Minecraft classes nên test được trên JVM thuần.
 */
class FlightConfigDefaultsTest {

    private val gson = GsonBuilder().create()

    @Test
    fun `useNativeNavigation defaults to false`() {
        assertFalse(FlightConfig().useNativeNavigation)
    }

    @Test
    fun `missing useNativeNavigation in old json reads as false`() {
        val oldJson = """{"flightSpeed":0.25,"preferredHeight":20.0,"maxFlightTicks":999999}"""
        val loaded = gson.fromJson(oldJson, FlightConfig::class.java)
        assertEquals(0.25, loaded.flightSpeed)
        assertEquals(20.0, loaded.preferredHeight)
        assertEquals(999999, loaded.maxFlightTicks)
        assertFalse(loaded.useNativeNavigation)
    }

    @Test
    fun `explicit true survives round trip`() {
        val config = FlightConfig(useNativeNavigation = true)
        val json = gson.toJson(config)
        val loaded = gson.fromJson(json, FlightConfig::class.java)
        assertTrue(loaded.useNativeNavigation)
    }

    @Test
    fun `preset map with legacy entries loads without crash`() {
        val legacyJson = """{"24":{"flightSpeed":0.25,"preferredHeight":20.0,"maxFlightTicks":999999}}"""
        val type = object : TypeToken<MutableMap<String, FlightConfig>>() {}.type
        val presets: MutableMap<String, FlightConfig> = gson.fromJson(legacyJson, type)
        assertEquals(1, presets.size)
        assertFalse(presets["24"]!!.useNativeNavigation)
    }
}
