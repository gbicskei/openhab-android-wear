package org.openhab.habdroid.wear.complication

import dagger.hilt.android.AndroidEntryPoint

/**
 * Icon-only complication data source (MONOCHROMATIC_IMAGE).
 * Appears in the complication picker as "wearOH Icon".
 * Config activity filters to items with MONOCHROMATIC_IMAGE in supportedTypes.
 */
@AndroidEntryPoint
class OpenHabComplicationIconService : OpenHabComplicationService()
