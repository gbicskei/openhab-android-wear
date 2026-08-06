package org.openhab.habdroid.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.openhab.habdroid.wear.data.api.TileStateEventSource
import org.openhab.habdroid.wear.util.AppLog
import org.openhab.habdroid.wear.data.icon.IconCompositor
import org.openhab.habdroid.wear.data.icon.IconResolver
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.data.repository.TilePreferenceStore
import javax.inject.Inject

private const val STATE_KEY_PAGE = "current_page"

/**
 * Wear OS Tile that displays openHAB items configured via wearTile metadata.
 * Supports multiple pages via loadAction state navigation.
 * Layout: title at top, item grid centered, mic/back button at bottom.
 */
@AndroidEntryPoint
class OpenHabTileService : TileService() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var credentialStore: CredentialStore

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var iconResolver: IconResolver

    @Inject
    lateinit var iconCompositor: IconCompositor

    @Inject
    lateinit var tilePreferenceStore: TilePreferenceStore

    @Inject
    lateinit var tileStateEventSource: TileStateEventSource

    @Inject
    lateinit var themeStore: org.openhab.habdroid.wear.data.repository.ThemeStore

    @Inject
    lateinit var voicePreferenceStore: org.openhab.habdroid.wear.data.repository.VoicePreferenceStore

    @Inject
    lateinit var itemCache: org.openhab.habdroid.wear.data.repository.ItemCache

    @Inject
    lateinit var watchStatusWriter: org.openhab.habdroid.wear.sync.WatchStatusWriter

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    /** Cache all tile items (all pages) for aggregate state calculation */
    private var allTileItems: List<TileItem> = emptyList()
    /** Cache items for current page only (used for resource generation) */
    private var currentPageItems: List<TileItem> = emptyList()
    /** Shared resource version */
    private var resourceVersion: String = "0"

    /** Theme color from user preferences (e.g. amber, blue, green) */
    private val themeColor: Int get() = runBlocking { themeStore.getTheme().color }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val startTime = System.currentTimeMillis()
            AppLog.d("TileNav", "=== onTileRequest start ===")

            // Read current page from persistent prefs
            val currentPage = tilePreferenceStore.currentPage.first()
            AppLog.d("TileNav", "currentPage=$currentPage lastClickableId=${requestParams.currentState.lastClickableId}")

            // Handle back button via loadAction (resets to main)
            val effectivePage = if (requestParams.currentState.lastClickableId == "nav_back") {
                AppLog.d("TileNav", "→ nav_back → main")
                tilePreferenceStore.setCurrentPage(TileItem.PAGE_MAIN)
                TileItem.PAGE_MAIN
            } else if (requestParams.currentState.lastClickableId?.startsWith("nav_page_") == true) {
                // Forward navigation via loadAction
                val targetPage = requestParams.currentState.lastClickableId!!.removePrefix("nav_page_")
                AppLog.d("TileNav", "→ nav_page → $targetPage")
                tilePreferenceStore.setCurrentPage(targetPage)
                targetPage
            } else {
                currentPage
            }

            // Load all items for resources, filter current page for layout
            val t1 = System.currentTimeMillis()
            var allItems = repository.getAvailableTileItems().getOrDefault(emptyList())
            val t2 = System.currentTimeMillis()
            AppLog.d("TileNav", "getAvailableTileItems: ${t2-t1}ms, ${allItems.size} items, cache=${allItems.isNotEmpty()}")

            // If states are stale (warm start from disk or after process restart), refresh now
            if (!itemCache.statesLoaded && allItems.isNotEmpty()) {
                AppLog.d("TileNav", "States stale — refreshing inline")
                repository.refreshStates()
                    .onSuccess {
                        allItems = itemCache.get() ?: allItems
                        AppLog.d("TileNav", "Inline state refresh done")
                    }
                    .onFailure { e ->
                        AppLog.w("TileNav", "Inline state refresh failed: ${e.message}")
                    }
            }

            allTileItems = allItems
            resourceVersion = System.currentTimeMillis().toString()

            val pageItems = allItems
                .filter { it.page == effectivePage }
                .sortedBy { it.slot }
                .let { items ->
                    val layout = items.firstOrNull()?.pageLayout ?: 7
                    items.take(layout)
                }
            val pageLayout = pageItems.firstOrNull()?.pageLayout ?: 7

            currentPageItems = pageItems

            AppLog.d("TileNav", "page=$effectivePage, pageItems=${pageItems.size}, layout=$pageLayout")
            val voiceEnabled = voicePreferenceStore.voiceCommandsEnabled.first()

            // Sync screen dimensions to phone for accurate tile preview
            val screenWidthDp = requestParams.deviceConfiguration.screenWidthDp
            if (screenWidthDp > 0) {
                watchStatusWriter.writeScreenWidthDp(screenWidthDp)
            }

            val tile = buildTile(pageItems, pageLayout, effectivePage, requestParams, voiceEnabled)
            AppLog.d("TileNav", "=== onTileRequest done: ${System.currentTimeMillis()-startTime}ms ===")

            // Ensure SSE is running (covers process restart while tile is visible)
            if (allItems.isNotEmpty()) {
                val watchSet = buildWatchedItemsSet(allItems)
                tileStateEventSource.watchedItems = watchSet
                tileStateEventSource.start {
                    getUpdater(this@OpenHabTileService).requestUpdate(OpenHabTileService::class.java)
                }
            }

            tile
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        serviceScope.future {
            val resStart = System.currentTimeMillis()
            AppLog.d("TileNav", "=== onTileResourcesRequest start, ${currentPageItems.size} page items ===")
            val resources = ResourceBuilders.Resources.Builder()
                .setVersion(resourceVersion)
            var hasFailedIcons = false

            // Load icons only for current page items (max 7)
            for (tileItem in currentPageItems) {
                val iconRef = tileItem.effectiveIcon
                val displayItem = tileItem.displayItem
                val state = displayItem.state
                val iconState = if (itemCache.statesLoaded) {
                    if (tileItem.isPageNavigation) {
                        // Priority: valueItem state > own state > aggregate (if enabled)
                        val isActive = when {
                            tileItem.valueItemName != null -> tileItem.isDisplayActive
                            displayItem.state !in listOf("NULL", "UNDEF") -> tileItem.isDisplayActive
                            tileItem.aggregateState -> {
                                val targetPage = tileItem.targetPage
                                targetPage != null && allTileItems
                                    .filter { it.page == targetPage && !it.isPageNavigation }
                                    .any { it.isDisplayActive }
                            }
                            else -> false
                        }
                        AppLog.d("TileNav", "NAV ${tileItem.item.name}: state=${displayItem.state} valueItem=${tileItem.valueItemName} aggregate=${tileItem.aggregateState} isActive=$isActive")
                        if (isActive) org.openhab.habdroid.wear.data.icon.IconState.ACTIVE
                        else org.openhab.habdroid.wear.data.icon.IconState.NEUTRAL
                    } else if (tileItem.valueDisplay == org.openhab.habdroid.wear.data.model.ValueDisplay.COLOR) {
                        val result = tileItem.isDisplayActive
                        AppLog.d("TileNav", "ITEM ${tileItem.item.name}: displayState=${displayItem.state} invertValue=${tileItem.invertValue} isActive=$result")
                        if (result) org.openhab.habdroid.wear.data.icon.IconState.ACTIVE
                        else org.openhab.habdroid.wear.data.icon.IconState.INACTIVE
                    } else if (tileItem.valueDisplay == org.openhab.habdroid.wear.data.model.ValueDisplay.NONE) {
                        AppLog.d("TileNav", "ITEM ${tileItem.item.name}: NEUTRAL (stateDisplay=none)")
                        org.openhab.habdroid.wear.data.icon.IconState.NEUTRAL
                    } else {
                        AppLog.d("TileNav", "ITEM ${tileItem.item.name}: NEUTRAL (valueDisplay=${tileItem.valueDisplay})")
                        org.openhab.habdroid.wear.data.icon.IconState.NEUTRAL
                    }
                } else org.openhab.habdroid.wear.data.icon.IconState.INACTIVE
                val resourceId = if (tileItem.isPageNavigation) {
                    "icon_nav_${tileItem.targetPage ?: tileItem.slot}"
                } else {
                    "icon_${tileItem.item.name}"
                }
                val label = tileItem.effectiveLabel

                // Determine state text based on valueDisplay and item type
                val stateText = when {
                    tileItem.isPageNavigation -> null
                    tileItem.valueDisplay == org.openhab.habdroid.wear.data.model.ValueDisplay.COLOR -> null
                    tileItem.valueDisplay == org.openhab.habdroid.wear.data.model.ValueDisplay.NONE -> null
                    tileItem.isRangeControl -> formatRangeState(displayItem)
                    else -> formatState(state)
                }

                val rawBytes = iconResolver.resolve(iconRef, state)
                val composited = if (rawBytes != null) {
                    val format = iconResolver.detectFormat(rawBytes)
                    iconCompositor.composite(rawBytes, format, iconState, themeColor, label, stateText)
                } else null

                val imageBytes = composited
                    ?: iconCompositor.fallback(iconState, themeColor, label, stateText)

                if (imageBytes != null) {
                    resources.addIdToImageMapping(
                        resourceId,
                        ResourceBuilders.ImageResource.Builder()
                            .setInlineResource(
                                ResourceBuilders.InlineImageResource.Builder()
                                    .setData(imageBytes)
                                    .setWidthPx(IconCompositor.SIZE)
                                    .setHeightPx(IconCompositor.SIZE)
                                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                                    .build()
                            )
                            .build()
                    )
                }

                if (rawBytes == null || composited == null) {
                    hasFailedIcons = true
                    AppLog.w("TileNav", "Icon failed for '${tileItem.item.name}': iconRef='$iconRef' " +
                        "resolve=${if (rawBytes != null) "OK (${rawBytes.size}B)" else "FAILED"}, " +
                        "composite=${if (composited != null) "OK" else "FAILED"}")
                }
            }

            // Load static mic icon from assets
            loadMicIconResource(resources)

            // Load openHAB logo for title area
            loadLogoResource(resources)
            loadPlainLogoResource(resources)

            AppLog.d("TileNav", "=== onTileResourcesRequest done: ${System.currentTimeMillis()-resStart}ms ===")

            // If any icons failed to load (network), schedule a retry after 5s
            if (hasFailedIcons) {
                AppLog.d("TileNav", "Some icons failed — scheduling retry in 5s")
                serviceScope.launch {
                    kotlinx.coroutines.delay(5000)
                    getUpdater(this@OpenHabTileService).requestUpdate(OpenHabTileService::class.java)
                }
            }

            resources.build()
        }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(requestParams)
        AppLog.d("TileNav", "onTileEnterEvent — invalidating states, fetching fresh")

        // Invalidate states — tile renders dimmed until fresh states arrive
        itemCache.invalidateStates()

        // Request a tile refresh (will render with stale/dimmed state)
        getUpdater(this).requestUpdate(OpenHabTileService::class.java)

        // Fetch fresh states in background (single batch call), then refresh tile (lit)
        serviceScope.launch {
            repository.refreshStates()
                .onSuccess {
                    AppLog.d("TileNav", "Fresh states loaded, requesting lit render")
                    getUpdater(this@OpenHabTileService).requestUpdate(OpenHabTileService::class.java)
                }
                .onFailure { e ->
                    AppLog.w("TileNav", "Failed to fetch states: ${e.message}")
                }

            // Build watched items set including Group members
            val watchSet = buildWatchedItemsSet(allTileItems)
            tileStateEventSource.watchedItems = watchSet

            // Start SSE (handles reconnection + polling fallback internally)
            tileStateEventSource.start {
                // Cache already updated by TileStateEventSource — just re-render
                getUpdater(this@OpenHabTileService).requestUpdate(OpenHabTileService::class.java)
            }
        }
    }

    override fun onTileLeaveEvent(requestParams: EventBuilders.TileLeaveEvent) {
        super.onTileLeaveEvent(requestParams)
        // Stop SSE/polling when tile is no longer visible
        tileStateEventSource.stop()
    }

    /**
     * Build the set of item names to watch for SSE state changes.
     * Includes display items, action items, and Group members.
     */
    private fun buildWatchedItemsSet(items: List<TileItem>): Set<String> {
        val names = mutableSetOf<String>()
        for (item in items) {
            names.add(item.displayItemName)
            item.commandItemName?.let { names.add(it) }
            // Include Group members so SSE picks up their state changes
            item.item.members?.forEach { member -> names.add(member.name) }
            item.valueItem?.members?.forEach { member -> names.add(member.name) }
        }
        return names
    }

    private fun buildTile(items: List<TileItem>, pageLayout: Int, currentPage: String, requestParams: RequestBuilders.TileRequest, voiceEnabled: Boolean): TileBuilders.Tile {
        // Get screen dimensions for responsive layout
        val deviceParams = requestParams.deviceConfiguration
        // Galaxy Watch Ultra: 481px at 340dpi → 226dp
        val screenW = deviceParams.screenWidthDp.toFloat().takeIf { it > 0f } ?: 226f
        val screenH = deviceParams.screenHeightDp.toFloat().takeIf { it > 0f } ?: 226f

        val layout = if (items.isEmpty() && currentPage == TileItem.PAGE_MAIN) {
            buildEmptyState()
        } else {
            buildPageLayout(items, pageLayout, currentPage, screenW, screenH, itemCache.statesLoaded, voiceEnabled)
        }

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(layout)
                            .build()
                    )
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(resourceVersion)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(30_000)
            .build()
    }

    private fun buildEmptyState(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(dp(10f))
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId(RESOURCE_ID_LOGO)
                            .setWidth(dp(36f))
                            .setHeight(dp(36f))
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(dp(60f))
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("No items configured.\nUse the phone companion app\nto set up your tile.")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setColor(argb(0xFFCCCCCC.toInt()))
                                    .build()
                            )
                            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    /**
     * Build layout for a specific page.
     * Buttons are positioned at absolute screen coordinates.
     * Title and mic are overlays that don't affect button positioning.
     */
    private fun buildPageLayout(items: List<TileItem>, pageLayout: Int, currentPage: String, screenW: Float, screenH: Float, isLive: Boolean, voiceEnabled: Boolean): LayoutElementBuilders.LayoutElement {
        val title = if (currentPage == TileItem.PAGE_MAIN) "openHAB"
            else repository.pageLabels[currentPage]?.takeIf { it.isNotBlank() }
                ?: currentPage.replaceFirstChar { it.uppercase() }

        // Use the configured layout count for position computation (not items.size)
        // This ensures correct spacing even when some slots are empty
        val count = pageLayout.coerceIn(1, 7)

        // Compute absolute button center positions (x, y) and button size for this layout
        val (rawPositions, btnSize) = computePositions(count, screenW, screenH)

        // When mic is hidden on main page, shift buttons down to vertically center
        // in the expanded space (no bottom element consuming ~38dp)
        val positions = if (currentPage == TileItem.PAGE_MAIN && !voiceEnabled) {
            val yShift = 10f // subtle shift down into freed mic space
            rawPositions.map { (x, y) -> x to y + yShift }
        } else {
            rawPositions
        }

        AppLog.d("TilePos", "=== buildPageLayout ===")

        // Single Box = full screen. Buttons + title + mic all stacked as overlays.
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(screenW))
            .setHeight(dp(screenH))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)

        // Add each button at its absolute position
        val halfBtn = btnSize / 2f
        val iconSize = btnSize - 4f

        for ((index, pos) in positions.withIndex()) {
            val (cx, cy) = pos
            val padLeft = (cx - halfBtn).coerceAtLeast(0f)
            val padTop = (cy - halfBtn).coerceAtLeast(0f)

            val slotNumber = index + 1
            val tileItem = items.find { it.slot == slotNumber } ?: continue

            AppLog.d("TilePos", "  render btn$slotNumber: center=($cx,$cy) padLeft=$padLeft padTop=$padTop item=${tileItem.item.name}")

            root.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(screenW))
                    .setHeight(dp(screenH))
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                ModifiersBuilders.Padding.Builder()
                                    .setStart(dp(padLeft))
                                    .setTop(dp(padTop))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(buildItemButton(tileItem, iconSize, btnSize, isLive))
                    .build()
            )
        }

        // Title overlay (top center) with logo as background — positioned absolutely
        val titleCenterX = screenW / 2f
        val titleCenterY = 22f // Push group toward very top
        val titleBoxW = 120f
        val titleBoxH = 36f
        val titlePadLeft = (titleCenterX - titleBoxW / 2f).coerceAtLeast(0f)
        val titlePadTop = (titleCenterY - titleBoxH / 2f).coerceAtLeast(0f)

        root.addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(dp(screenW))
                .setHeight(dp(screenH))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(dp(titlePadLeft))
                                .setTop(dp(titlePadTop))
                                .build()
                        )
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dp(titleBoxW))
                        .setHeight(dp(titleBoxH))
                        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                        .addContent(
                            if (currentPage == TileItem.PAGE_MAIN) {
                                // Wordmark replaces logo+text on main page
                                LayoutElementBuilders.Image.Builder()
                                    .setResourceId(RESOURCE_ID_LOGO)
                                    .setWidth(dp(36f * 37.945313f / 31.791088f))
                                    .setHeight(dp(36f))
                                    .build()
                            } else {
                                // Sub-pages: plain logo behind the page title text
                                LayoutElementBuilders.Box.Builder()
                                    .setWidth(dp(titleBoxW))
                                    .setHeight(dp(titleBoxH))
                                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                    .addContent(
                                        LayoutElementBuilders.Image.Builder()
                                            .setResourceId(RESOURCE_ID_PLAIN_LOGO)
                                            .setWidth(dp(titleBoxH))
                                            .setHeight(dp(titleBoxH))
                                            .build()
                                    )
                                    .addContent(
                                        LayoutElementBuilders.Text.Builder()
                                            .setText(title)
                                            .setFontStyle(
                                                LayoutElementBuilders.FontStyle.Builder()
                                                    .setSize(sp(12f))
                                                    .setColor(argb(if (isLive) 0xFFFFFFFF.toInt() else 0xFF666666.toInt()))
                                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            }
                        )
                        .build()
                )
                .build()
        )

        // Bottom overlay: mic on main page (if voice enabled), back button on sub-pages
        val showBottomButton = currentPage != TileItem.PAGE_MAIN || voiceEnabled
        if (showBottomButton) {
            root.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(screenW))
                    .setHeight(dp(screenH))
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                ModifiersBuilders.Padding.Builder()
                                    .setBottom(dp(10f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        if (currentPage == TileItem.PAGE_MAIN) buildMicButton()
                        else buildBackButton()
                    )
                    .build()
            )
        }

        return root.build()
    }

    /**
     * Compute absolute (x, y) center positions for each button slot.
     * Returns positions and the button size for this layout.
     * All values in dp, relative to screen top-left (0,0).
     * Screen center is the anchor point.
     */
    private fun computePositions(count: Int, screenW: Float, screenH: Float): Pair<List<Pair<Float, Float>>, Float> {
        val btn = 64f
        val centerX = screenW / 2f
        val centerY = screenH / 2f

        AppLog.d("TilePos", "=== computePositions count=$count screenW=$screenW screenH=$screenH ===")
        AppLog.d("TilePos", "btn=$btn centerX=$centerX centerY=$centerY")

        val (positions, layoutBtn) = when (count) {
            1 -> {
                listOf(centerX to centerY) to 74f
            }
            2 -> {
                // 2 buttons horizontal, 74dp, 4dp gap (same as other layouts)
                val btn2 = 74f
                val spacing = btn2 + 4f
                val halfSpacing = spacing / 2f
                AppLog.d("TilePos", "layout2: btn2=$btn2 spacing=$spacing")
                listOf(
                    centerX - halfSpacing to centerY,
                    centerX + halfSpacing to centerY
                ) to btn2
            }
            3 -> {
                // Staggered V-shape: side buttons up, center button down
                // Larger buttons (74dp) enabled by vertical separation
                val btn3 = 74f
                val vShift = btn3 * 0.42f
                val sideY = centerY + vShift
                val centerBtnY = centerY - vShift
                val hPos = computeHorizontal2(screenW, btn3, edgeRatio = 1.5f)
                AppLog.d("TilePos", "layout3: btn3=$btn3 sideY=$sideY centerBtnY=$centerBtnY hPos=${hPos.toList()}")
                listOf(
                    hPos[0] to sideY,
                    centerX to centerBtnY,
                    hPos[1] to sideY
                ) to btn3
            }
            4 -> {
                // 2x2 square grid with 74dp buttons, matched spacing to layout 3
                val btn4 = 74f
                val spacing = btn4 + 4f // 4dp gap between adjacent buttons
                val halfSpacing = spacing / 2f
                // Shift grid up slightly to clear mic button
                val gridCenterY = centerY - 4f
                AppLog.d("TilePos", "layout4: btn4=$btn4 spacing=$spacing gridCenterY=$gridCenterY")
                listOf(
                    centerX - halfSpacing to gridCenterY - halfSpacing,
                    centerX + halfSpacing to gridCenterY - halfSpacing,
                    centerX - halfSpacing to gridCenterY + halfSpacing,
                    centerX + halfSpacing to gridCenterY + halfSpacing
                ) to btn4
            }
            5 -> {
                // 2 top + center + 2 bottom, edge_ratio=1.0
                val hPos = computeHorizontal2(screenW, btn, edgeRatio = 1.0f)
                val gap = computeGap2(screenW, btn, 1.0f)
                val vOffset = (btn + gap) / 2f
                AppLog.d("TilePos", "layout5: hPos=${hPos.toList()} gap=$gap vOffset=$vOffset")
                listOf(
                    hPos[0] to centerY - vOffset,
                    centerX to centerY,
                    hPos[1] to centerY - vOffset,
                    hPos[0] to centerY + vOffset,
                    hPos[1] to centerY + vOffset
                ) to btn
            }
            6 -> {
                // 7-item positions minus center button
                val mid = computeHorizontal3(screenW, btn, edgeRatio = 0.6f)
                val topBottomX = floatArrayOf((mid[0] + mid[1]) / 2f, (mid[1] + mid[2]) / 2f)
                val yOffset = btn * 0.85f // Fixed vertical spacing
                AppLog.d("TilePos", "layout6: mid=${mid.toList()} topBottomX=${topBottomX.toList()} yOffset=$yOffset")
                listOf(
                    topBottomX[0] to centerY - yOffset,
                    topBottomX[1] to centerY - yOffset,
                    mid[0] to centerY,
                    mid[2] to centerY,
                    topBottomX[0] to centerY + yOffset,
                    topBottomX[1] to centerY + yOffset
                ) to btn
            }
            7 -> {
                // Full: 2 top + 3 middle + 2 bottom
                val mid = computeHorizontal3(screenW, btn, edgeRatio = 0.6f)
                val topBottomX = floatArrayOf((mid[0] + mid[1]) / 2f, (mid[1] + mid[2]) / 2f)
                val yOffset = btn * 0.85f // Fixed vertical spacing
                AppLog.d("TilePos", "layout7: mid=${mid.toList()} topBottomX=${topBottomX.toList()} yOffset=$yOffset")
                listOf(
                    topBottomX[0] to centerY - yOffset,
                    topBottomX[1] to centerY - yOffset,
                    mid[0] to centerY,
                    mid[1] to centerY,
                    mid[2] to centerY,
                    topBottomX[0] to centerY + yOffset,
                    topBottomX[1] to centerY + yOffset
                ) to btn
            }
            else -> emptyList<Pair<Float, Float>>() to btn
        }

        for ((i, pos) in positions.withIndex()) {
            AppLog.d("TilePos", "  btn${i+1}: x=${pos.first} y=${pos.second}")
        }

        return positions to layoutBtn
    }

    /** Compute center X positions for 3 buttons with given edge ratio. */
    private fun computeHorizontal3(screenW: Float, btn: Float, edgeRatio: Float): FloatArray {
        val remaining = screenW - 3 * btn
        val g = remaining / (2 * edgeRatio + 2)
        val edge = g * edgeRatio
        return floatArrayOf(
            edge + btn / 2f,
            screenW / 2f,
            screenW - edge - btn / 2f
        )
    }

    /** Compute center X positions for 2 buttons with given edge ratio. */
    private fun computeHorizontal2(screenW: Float, btn: Float, edgeRatio: Float): FloatArray {
        val remaining = screenW - 2 * btn
        val g = remaining / (2 * edgeRatio + 1)
        val edge = g * edgeRatio
        return floatArrayOf(
            edge + btn / 2f,
            screenW - edge - btn / 2f
        )
    }

    /** Compute center gap for 2-button layout. */
    private fun computeGap2(screenW: Float, btn: Float, edgeRatio: Float): Float {
        val remaining = screenW - 2 * btn
        return remaining / (2 * edgeRatio + 1)
    }


    private fun buildMicButton(): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("voice_command")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName("org.openhab.habdroid.wear.ui.voice.VoiceCommandActivity")
                            .setPackageName("org.openhab.habdroid.wear")
                            .build()
                    )
                    .build()
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(48f))
            .setHeight(dp(28f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF333333.toInt()))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(14f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(RESOURCE_ID_MIC)
                    .setWidth(dp(16f))
                    .setHeight(dp(16f))
                    .build()
            )
            .build()
    }

    /**
     * Back button shown at the bottom of sub-pages. Uses loadAction to navigate back to main.
     */
    private fun buildBackButton(): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("nav_back")
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(48f))
            .setHeight(dp(28f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF333333.toInt()))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(14f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("\u2190")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(16f))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildItemButton(tileItem: TileItem, iconSize: Float = 48f, boxSize: Float = iconSize + 4f, isLive: Boolean = true): LayoutElementBuilders.LayoutElement {
        val item = tileItem.item
        val clickable = if (isLive) buildItemClickable(tileItem) else ModifiersBuilders.Clickable.Builder().setId("disabled_${item.name}").build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(boxSize))
            .setHeight(dp(boxSize))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(dp(2f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(
                        if (tileItem.isPageNavigation) "icon_nav_${tileItem.targetPage ?: tileItem.slot}"
                        else "icon_${item.name}"
                    )
                    .setWidth(dp(iconSize))
                    .setHeight(dp(iconSize))
                    .build()
            )
            .build()
    }

    /**
     * Builds the click action for a tile item.
     * Navigation buttons → loadAction (re-renders tile with new page).
     * Contact items → no action (display only).
     * Range items → launch RotaryControlActivity.
     * Color items → launch ColorPickerActivity.
     * Rollershutter items → launch RollerShutterActivity.
     * Items with commandOptions → launch ChoicePickerActivity.
     * Switch/toggle items → launch TileActionReceiver (toggle/command).
     */
    private fun buildItemClickable(tileItem: TileItem): ModifiersBuilders.Clickable {
        val item = tileItem.item

        return when {
            tileItem.isPageNavigation -> {
                if (tileItem.needsConfirmation) {
                    // Needs confirmation → launch Activity for dialog
                    ModifiersBuilders.Clickable.Builder()
                        .setId("nav_page_${tileItem.targetPage}")
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setClassName("org.openhab.habdroid.wear.tile.PageNavigationActivity")
                                        .setPackageName("org.openhab.habdroid.wear")
                                        .addKeyToExtraMapping(
                                            PageNavigationActivity.EXTRA_TARGET_PAGE,
                                            ActionBuilders.AndroidStringExtra.Builder()
                                                .setValue(tileItem.targetPage ?: TileItem.PAGE_MAIN)
                                                .build()
                                        )
                                        .addKeyToExtraMapping(
                                            PageNavigationActivity.EXTRA_NEEDS_CONFIRMATION,
                                            ActionBuilders.AndroidBooleanExtra.Builder()
                                                .setValue(true)
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                } else {
                    // No confirmation → use LoadAction for instant navigation
                    ModifiersBuilders.Clickable.Builder()
                        .setId("nav_page_${tileItem.targetPage}")
                        .setOnClick(ActionBuilders.LoadAction.Builder().build())
                        .build()
                }
            }
            item.isContact -> {
                // Contact item — display only, no action
                ModifiersBuilders.Clickable.Builder()
                    .setId("contact_${item.name}")
                    .build()
            }
            tileItem.isRangeControl -> {
                // Range item → open rotary control screen
                ModifiersBuilders.Clickable.Builder()
                    .setId("range_${item.name}")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setClassName("org.openhab.habdroid.wear.ui.control.RotaryControlActivity")
                                    .setPackageName("org.openhab.habdroid.wear")
                                    .addKeyToExtraMapping(
                                        "item_name",
                                        ActionBuilders.AndroidStringExtra.Builder()
                                            .setValue(tileItem.commandTargetName)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            }
            item.type == "Color" -> {
                // Color item → open color picker
                ModifiersBuilders.Clickable.Builder()
                    .setId("color_${item.name}")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setClassName("org.openhab.habdroid.wear.ui.control.ColorPickerActivity")
                                    .setPackageName("org.openhab.habdroid.wear")
                                    .addKeyToExtraMapping(
                                        "item_name",
                                        ActionBuilders.AndroidStringExtra.Builder()
                                            .setValue(tileItem.commandTargetName)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            }
            item.type == "Rollershutter" -> {
                // Rollershutter item → open roller shutter control
                ModifiersBuilders.Clickable.Builder()
                    .setId("shutter_${item.name}")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setClassName("org.openhab.habdroid.wear.ui.control.RollerShutterActivity")
                                    .setPackageName("org.openhab.habdroid.wear")
                                    .addKeyToExtraMapping(
                                        "item_name",
                                        ActionBuilders.AndroidStringExtra.Builder()
                                            .setValue(tileItem.commandTargetName)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            }
            item.commandDescription?.commandOptions?.isNotEmpty() == true -> {
                // Item with command options → open choice picker
                ModifiersBuilders.Clickable.Builder()
                    .setId("choice_${item.name}")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setClassName("org.openhab.habdroid.wear.ui.control.ChoicePickerActivity")
                                    .setPackageName("org.openhab.habdroid.wear")
                                    .addKeyToExtraMapping(
                                        "item_name",
                                        ActionBuilders.AndroidStringExtra.Builder()
                                            .setValue(tileItem.commandTargetName)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            }
            else -> {
                // Switch/command item → toggle or send fixed command
                if (tileItem.needsConfirmation) {
                    // Needs confirmation → bake in the command since the dialog shows before executing
                    val command = when {
                        tileItem.isCommand && tileItem.commandValue != null -> tileItem.commandValue
                        tileItem.displayItem.isActive -> "OFF"
                        else -> "ON"
                    }
                    ModifiersBuilders.Clickable.Builder()
                        .setId("confirm_${item.name}")
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setClassName("org.openhab.habdroid.wear.tile.TileActionReceiver")
                                        .setPackageName("org.openhab.habdroid.wear")
                                        .addKeyToExtraMapping(
                                            "item_name",
                                            ActionBuilders.AndroidStringExtra.Builder()
                                                .setValue(tileItem.commandTargetName)
                                                .build()
                                        )
                                        .addKeyToExtraMapping(
                                            "command",
                                            ActionBuilders.AndroidStringExtra.Builder()
                                                .setValue(command)
                                                .build()
                                        )
                                        .addKeyToExtraMapping(
                                            "needs_confirmation",
                                            ActionBuilders.AndroidBooleanExtra.Builder()
                                                .setValue(true)
                                                .build()
                                        )
                                        .addKeyToExtraMapping(
                                            "label",
                                            ActionBuilders.AndroidStringExtra.Builder()
                                                .setValue(tileItem.effectiveLabel)
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                } else if (tileItem.isCommand && tileItem.commandValue != null) {
                    // Fixed command → always send the same command regardless of state
                    ModifiersBuilders.Clickable.Builder()
                        .setId("cmd_${item.name}")
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setClassName("org.openhab.habdroid.wear.tile.TileActionReceiver")
                                        .setPackageName("org.openhab.habdroid.wear")
                                        .addKeyToExtraMapping(
                                            "item_name",
                                            ActionBuilders.AndroidStringExtra.Builder()
                                                .setValue(tileItem.commandTargetName)
                                                .build()
                                        )
                                        .addKeyToExtraMapping(
                                            "command",
                                            ActionBuilders.AndroidStringExtra.Builder()
                                                .setValue(tileItem.commandValue)
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                } else {
                    // Toggle item → don't bake in command; let TileActionReceiver
                    // fetch fresh state at tap time to avoid stale toggle direction
                    ModifiersBuilders.Clickable.Builder()
                        .setId("toggle_${item.name}")
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setClassName("org.openhab.habdroid.wear.tile.TileActionReceiver")
                                        .setPackageName("org.openhab.habdroid.wear")
                                        .addKeyToExtraMapping(
                                            "item_name",
                                            ActionBuilders.AndroidStringExtra.Builder()
                                                .setValue(tileItem.commandTargetName)
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                }
            }
        }
    }

    private fun formatRangeState(item: Item): String {
        val value = item.numericState
        val pattern = item.stateDescription?.pattern
        return when {
            value == null -> "\u2013"
            pattern != null -> try {
                String.format(pattern.replace("%unit%", ""), value.let {
                    if (it == it.toLong().toDouble()) it.toLong() else it
                })
            } catch (e: Exception) {
                value.toInt().toString()
            }
            // Infer unit suffix from item type when no pattern is available
            item.type == "Dimmer" || item.type == "Rollershutter" -> "${value.toInt()}%"
            item.type.startsWith("Number:Temperature") -> "${String.format("%.0f", value)}°"
            value == value.toLong().toDouble() -> value.toLong().toString()
            else -> String.format("%.1f", value)
        }
    }

    private fun formatState(state: String): String {
        return when {
            state == "NULL" || state == "UNDEF" -> "\u2013"
            state.length > 6 -> state.take(6) + "\u2026"
            else -> state
        }
    }

    /**
     * Loads the mic SVG from assets and registers it as a tile image resource.
     * Renders the SVG as white-on-transparent at 16x16px for the pill button.
     */
    private fun loadMicIconResource(resources: ResourceBuilders.Resources.Builder) {
        try {
            val svgBytes = applicationContext.assets.open("ic_mic.svg").use { it.readBytes() }
            val size = MIC_ICON_SIZE
            val svg = com.caverock.androidsvg.SVG.getFromString(String(svgBytes, Charsets.UTF_8))
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            svg.documentWidth = size.toFloat()
            svg.documentHeight = size.toFloat()
            svg.renderToCanvas(canvas)

            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()

            resources.addIdToImageMapping(
                RESOURCE_ID_MIC,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(stream.toByteArray())
                            .setWidthPx(size)
                            .setHeightPx(size)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                            .build()
                    )
                    .build()
            )
        } catch (e: Exception) {
            AppLog.w("TileNav", "Failed to load mic icon: ${e.message}")
        }
    }

    /**
     * Loads the openHAB logo SVG from assets and registers it as an inline image
     * for the title area. Rendered at specified pixel size with optional alpha.
     */
    private fun loadLogoResource(resources: ResourceBuilders.Resources.Builder) {
        try {
            val svgBytes = applicationContext.assets.open("ic_openhab_logo_v5.svg").use { it.readBytes() }
            val svg = com.caverock.androidsvg.SVG.getFromString(String(svgBytes, Charsets.UTF_8))
            // Wordmark aspect ratio ~37.95:31.79 → use height=LOGO_ICON_SIZE, width proportional
            val height = LOGO_ICON_SIZE
            val width = (height * 37.945313f / 31.791088f).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            svg.documentWidth = width.toFloat()
            svg.documentHeight = height.toFloat()
            svg.renderToCanvas(canvas)

            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()

            resources.addIdToImageMapping(
                RESOURCE_ID_LOGO,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(stream.toByteArray())
                            .setWidthPx(width)
                            .setHeightPx(height)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                            .build()
                    )
                    .build()
            )
        } catch (e: Exception) {
            AppLog.w("TileNav", "Failed to load logo icon: ${e.message}")
        }
    }

    private fun loadPlainLogoResource(resources: ResourceBuilders.Resources.Builder) {
        try {
            val size = LOGO_ICON_SIZE
            val svgBytes = applicationContext.assets.open("ic_openhab_logo.svg").use { it.readBytes() }
            val svg = com.caverock.androidsvg.SVG.getFromString(String(svgBytes, Charsets.UTF_8))
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            svg.documentWidth = size.toFloat()
            svg.documentHeight = size.toFloat()
            svg.renderToCanvas(canvas)

            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()

            resources.addIdToImageMapping(
                RESOURCE_ID_PLAIN_LOGO,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(stream.toByteArray())
                            .setWidthPx(size)
                            .setHeightPx(size)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                            .build()
                    )
                    .build()
            )
        } catch (e: Exception) {
            AppLog.w("TileNav", "Failed to load plain logo icon: ${e.message}")
        }
    }

    companion object {
        private const val RESOURCE_ID_MIC = "ic_mic"
        private const val MIC_ICON_SIZE = 16
        private const val RESOURCE_ID_LOGO = "ic_logo"
        private const val RESOURCE_ID_PLAIN_LOGO = "ic_plain_logo"
        private const val LOGO_ICON_SIZE = 108
    }
}
