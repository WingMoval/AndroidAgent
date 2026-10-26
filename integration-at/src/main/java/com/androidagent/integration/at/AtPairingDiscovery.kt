package com.androidagent.integration.at

/**
 * AG integration-owned pairing discovery result (V4 consumer boundary).
 *
 * Maps the AT composition `ATAdbPairingDiscovery` to an AG-owned type.
 */
data class AtPairingDiscovery(
    val endpointCount: Int,
    val serviceNotFound: Boolean,
    val multiple: Boolean,
)
