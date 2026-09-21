package com.toancao.pokemonai.flight

data class FlightConfig(
    val flightSpeed: Double = 0.5,
    val preferredHeight: Double = 10.0,
    val maxFlightTicks: Int = 1200,
    val baseTakeoffChance: Double = 0.08 + (preferredHeight / 500.0),
    val baseLandingChance: Double = 0.12 * (600.0 / maxFlightTicks),
    val waterHoverChance: Double = baseTakeoffChance * 1.5,
    val verticalSway: Double = 0.03,
    val directionChangeInterval: Int = 100,
    val takeoffDuration: Int = 40,
    val takeoffDampingFactor: Double = 0.35,
    val takeoffAcceleration: Double = 1.2,
    val landingDeceleration: Double = 0.05,
    val heightMultiplier: Double = 1.0,
    val activationRadius: Double = 128.0,

    val circularFlightChance: Double = 0.0,
    val circularFlightRadius: Double = 10.0,
    val circularFlightDuration: Int = 400,
    val canGroundHover: Boolean = false,
    val hoverOnly: Boolean = false,

    val lerpFactor: Double = 0.15,
    val obstacleAvoidance: Boolean = true,
    val obstacleCheckRange: Int = 4,
    val obstacleClimbAmount: Double = 1.5,
    val arriveThresholdHoriz: Double = 1.5,
    val arriveThresholdVert: Double = 1.0,
    val speedPlayerScale: Boolean = true,

    val dropOnHit: Boolean = true,
    val stopWhenFullySubmerged: Boolean = true,
    val dropOnWaterSurface: Boolean = false,
    val useExtraStaminaToContinueFlight: Boolean = true,

    val useNativeNavigation: Boolean = false
)
