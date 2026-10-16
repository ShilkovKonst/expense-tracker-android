package com.spendobserver.ui.stats

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.spendobserver.domain.format.formatAmount
import com.spendobserver.domain.format.formatWholeNumber
import com.spendobserver.domain.format.shortMonthName
import com.spendobserver.domain.stats.ChartBucket
import com.spendobserver.domain.stats.StatsPeriod
import com.spendobserver.ui.common.rememberFormatLocale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What the chart's Y axis measures. */
enum class ChartMetric { COUNT, AMOUNT }

/**
 * The bar chart for [period]: one bar per bucket, the Y value picked by
 * [metric]. A tap on a bar drills into it ([ChartBucket.drillDown]); on the
 * day level, where there's nothing finer, it shows the bar's value instead.
 */
@Composable
fun StatsChart(
    period: StatsPeriod,
    buckets: List<ChartBucket>,
    metric: ChartMetric,
    onDrillDown: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Vico picks its colors up from the Material 3 theme through this.
    ProvideVicoTheme(rememberM3VicoTheme()) {
        // A fresh chart per period: the tapped bar's marker and the axis
        // formatters belong to one level and must not carry over to the next.
        key(period) {
            PeriodChart(period, buckets, metric, onDrillDown, modifier)
        }
    }
}

@Composable
private fun PeriodChart(
    period: StatsPeriod,
    buckets: List<ChartBucket>,
    metric: ChartMetric,
    onDrillDown: (StatsPeriod) -> Unit,
    modifier: Modifier,
) {
    val values = buckets.map {
        when (metric) {
            ChartMetric.COUNT -> it.recordCount.toDouble()
            ChartMetric.AMOUNT -> it.amountCents / 100.0
        }
    }
    val maxValue = values.maxOrNull() ?: 0.0
    // Vico's formatters below aren't composable, so they get the locale handed in.
    val locale = rememberFormatLocale()

    // The producer owns the chart's data; each transaction re-draws it with an animation.
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(buckets, metric) {
        modelProducer.runTransaction {
            columnModel { series(x = buckets.map { it.key }, y = values) }
        }
    }

    val bottomFormatter = remember(period, locale) {
        CartesianValueFormatter { _, x, _ ->
            val key = x.roundToInt()
            when (period) {
                StatsPeriod.AllTime -> key.toString()
                is StatsPeriod.Year -> if (key in 1..12) shortMonthName(key, locale) else ""
                is StatsPeriod.Month -> key.toString()
            }
        }
    }
    // Whole values without a fraction, small amounts (an axis step under 1) with one.
    val startFormatter = remember(locale) {
        CartesianValueFormatter { _, y, _ ->
            if (y == floor(y)) formatWholeNumber(y.roundToLong(), locale) else formatAmount((y * 100).roundToLong(), locale)
        }
    }
    // Counts are whole numbers, so never step below 1; an all-zero chart gets the
    // same treatment, or its 0..1 range would be cut into fractional steps.
    val step = if (metric == ChartMetric.COUNT || maxValue == 0.0) maxOf(1.0, ceil(maxValue / 4)) else null
    val startItemPlacer = remember(step) { VerticalAxis.ItemPlacer.step({ step }) }
    // With every bar at zero the automatic Y range collapses; give it room for an empty grid.
    val rangeProvider = remember(maxValue == 0.0) {
        if (maxValue == 0.0) CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 1.0) else CartesianLayerRangeProvider.auto()
    }

    // Read through these in Vico's callbacks: they're created once per chart,
    // and would otherwise keep seeing the data from their first composition.
    val currentBuckets by rememberUpdatedState(buckets)
    val currentMetric by rememberUpdatedState(metric)
    val currentOnDrillDown by rememberUpdatedState(onDrillDown)
    val tapListener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) = drill(targets)
            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) = drill(targets)

            private fun drill(targets: List<CartesianMarker.Target>) {
                val key = targets.firstOrNull()?.x?.roundToInt() ?: return
                currentBuckets.firstOrNull { it.key == key }?.drillDown?.let(currentOnDrillDown)
            }
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val markerLabel = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp),
        padding = Insets(8.dp, 4.dp),
        background = rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(4.dp),
        ),
    )
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = remember(locale) {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                val key = targets.firstOrNull()?.x?.roundToInt()
                val bucket = currentBuckets.firstOrNull { it.key == key }
                when {
                    bucket == null -> ""
                    currentMetric == ChartMetric.COUNT -> bucket.recordCount.toString()
                    else -> formatAmount(bucket.amountCents, locale)
                }
            }
        },
    )

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        fill = Fill(primary),
                        thickness = 16.dp,
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                    ),
                ),
                // Year labels ("2026") are wider than the default 16dp gap between bars:
                // packed that tight, Vico hides every label that would overlap a neighbor.
                columnCollectionSpacing = if (period == StatsPeriod.AllTime) 40.dp else 16.dp,
                rangeProvider = rangeProvider,
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter, itemPlacer = startItemPlacer),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = bottomFormatter,
                // 28–31 day labels can't all fit: label every 5th day (1, 6, 11, …).
                itemPlacer = remember(period) {
                    HorizontalAxis.ItemPlacer.aligned(spacing = { if (period is StatsPeriod.Month) 5 else 1 })
                },
            ),
            marker = marker,
            markerVisibilityListener = tapListener,
            markerController = CartesianMarkerController.rememberToggleOnTap(),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        // Every bar on screen at once: no scrolling, shrink to fit but never stretch.
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        // minZoom/maxZoom too: their defaults (≥ Zoom.Content) would stretch a
        // lone bar — e.g. a tracker's only year — across the whole width.
        zoomState = run {
            val fitWithoutStretching = remember { Zoom.min(Zoom.fixed(), Zoom.Content) }
            rememberVicoZoomState(
                zoomEnabled = false,
                initialZoom = fitWithoutStretching,
                minZoom = fitWithoutStretching,
                maxZoom = fitWithoutStretching,
            )
        },
    )
}
