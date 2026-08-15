package org.openhab.habdroid.wear.complication

import dagger.hilt.android.AndroidEntryPoint

/**
 * Gauge/range complication data source (RANGED_VALUE).
 * Appears in the complication picker as "wearOH Gauge".
 * Config activity filters to items with RANGED_VALUE in supportedTypes.
 */
@AndroidEntryPoint
class OpenHabComplicationGaugeService : OpenHabComplicationService()
