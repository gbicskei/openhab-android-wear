package org.openhab.habdroid.wear.complication

import dagger.hilt.android.AndroidEntryPoint

/**
 * Text-based complication data source (SHORT_TEXT, LONG_TEXT).
 * Appears in the complication picker as "wearOH Text".
 * Config activity filters to items with SHORT_TEXT or LONG_TEXT in supportedTypes.
 */
@AndroidEntryPoint
class OpenHabComplicationTextService : OpenHabComplicationService()
