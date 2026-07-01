package eu.dotshell.pelo.generic.ui.screens.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.dotshell.pelo.generic.data.network.mapstyle.MapStyleCategory
import eu.dotshell.pelo.generic.data.offline.OfflineDataInfo
import eu.dotshell.pelo.generic.data.offline.OfflineDownloadState
import eu.dotshell.pelo.generic.data.offline.OfflineRepository
import eu.dotshell.pelo.generic.data.repository.offline.mapstyle.MapStyleCompat
import eu.dotshell.pelo.generic.service.TransportServiceProvider
import eu.dotshell.pelo.generic.ui.theme.AccentColor
import eu.dotshell.pelo.generic.ui.theme.PrimaryColor
import eu.dotshell.pelo.generic.ui.theme.SecondaryColor
import eu.dotshell.pelo.generic.ui.viewmodel.TransportViewModel
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun OfflineSettingsScreen(
    viewModel: TransportViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current
    val strings = StringProvider(context)
    val offlineDataInfo by viewModel.offlineDataInfo.collectAsState()
    val downloadState by viewModel.offlineDataManager.downloadState.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val offlineRepository = remember { OfflineRepository(context) }
    var selectedMapStyles by remember {
        mutableStateOf(
            offlineRepository.getSelectedMapStyles().ifEmpty { setOf(MapStyleCompat.POSITRON.key) })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Spacer(modifier = Modifier.height(60.dp))
            Icon(
                imageVector = if (offlineDataInfo.isAvailable) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                contentDescription = if (offlineDataInfo.isAvailable) strings["offline_available"] else strings["offline_unavailable"],
                tint = if (offlineDataInfo.isAvailable) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings["offline_mode"],
                color = SecondaryColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (offlineDataInfo.isAvailable)
                    strings["offline_available_message"]
                else
                    strings["offline_download_message"],
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(50.dp))

            // Status card
            if (offlineDataInfo.isAvailable) {
                OfflineStatusCard(offlineDataInfo)
                Spacer(modifier = Modifier.height(30.dp))
            }

            CategoryHeader(Icons.Default.Map, strings["map_styles_category"])

            MapStyleSelectionCard(
                selectedStyles = selectedMapStyles,
                downloadedStyles = offlineDataInfo.downloadedMapStyles,
                isDownloading = downloadState is OfflineDownloadState.Downloading,
                isEnabled = !isOffline,
                onStyleToggled = { styleKey, checked ->
                    var newSet = if (checked) selectedMapStyles + styleKey
                    else if (selectedMapStyles.size > 1) selectedMapStyles - styleKey
                    else selectedMapStyles
                    // Toujours forcer positron si aucun style n'est sélectionné
                    if (newSet.isEmpty()) newSet = setOf(MapStyleCompat.POSITRON.key)
                    selectedMapStyles = newSet
                    offlineRepository.setSelectedMapStyles(newSet)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Download Section
            when (val state = downloadState) {
                is OfflineDownloadState.Downloading -> {
                    DownloadProgressCard(state, strings)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { viewModel.cancelOfflineDownload() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = strings["cancel_download"],
                            modifier = Modifier.size(18.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings["cancel"], color = Color.Gray, fontSize = 14.sp)
                    }
                }

                else -> {
                    Button(
                        enabled = !isOffline,
                        onClick = { viewModel.startOfflineDownload() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isOffline) 0.5f else 1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentColor,
                            disabledContainerColor = Color(0xFF3A3A3C)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            strings["download_data"],
                            modifier = Modifier.size(20.dp),
                            tint = SecondaryColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (offlineDataInfo.isAvailable) strings["update_button"] else strings["download_data"],
                            fontSize = 16.sp,
                            color = SecondaryColor,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (state is OfflineDownloadState.Error) {
                        Text(
                            translateOfflineErrorMessage(state.message, strings),
                            color = AccentColor,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 4.dp, top = 8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, strings["back"], tint = SecondaryColor)
        }
    }
}

@Composable
private fun CategoryHeader(icon: ImageVector, title: String) {
    val strings = StringProvider(LocalPlatformContext.current)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(
            icon,
            strings["category_header"],
            tint = SecondaryColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = SecondaryColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadProgressCard(state: OfflineDownloadState.Downloading, strings: StringProvider) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "progress"
    )

    val progressColor = lerp(Color(0xFFEF4444), Color(0xFF4CAF50), animatedProgress)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(7.dp))

            Text(
                translateOfflineStepDescription(state.stepDescription, strings),
                color = SecondaryColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            WavyLinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.fillMaxWidth(),
                indicatorColor = progressColor,
                trackColor = Color(0xFF3A3A3C)
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MapStyleSelectionCard(
    selectedStyles: Set<String>,
    downloadedStyles: Set<String>,
    isDownloading: Boolean,
    isEnabled: Boolean,
    onStyleToggled: (String, Boolean) -> Unit
) {
    val strings = StringProvider(LocalPlatformContext.current)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            val mapStyleConfig = TransportServiceProvider.getMapStyleConfig()
            val styles = MapStyleCompat.getByCategory(MapStyleCategory.STANDARD, mapStyleConfig)
            styles.forEachIndexed { index, style ->
                val isSelected = style.key in selectedStyles
                val isDownloaded = style.key in downloadedStyles
                val isInteractive = isEnabled && !isDownloading

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isInteractive) {
                            onStyleToggled(
                                style.key,
                                !isSelected
                            )
                        }
                        .padding(vertical = 16.dp, horizontal = 12.dp)
                        .alpha(if (isInteractive) 1f else 0.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Case carrée custom
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                2.dp,
                                if (isSelected) Color.Transparent else Color(0xFF8E8E93),
                                RoundedCornerShape(6.dp)
                            )
                            .background(if (isSelected) AccentColor else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(
                            Icons.Default.Check,
                            strings["selected"],
                            tint = SecondaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        style.displayName,
                        color = SecondaryColor,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )

                    if (isDownloaded && isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            strings["downloaded"],
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (index < styles.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp)
                            .height(0.5.dp)
                            .background(Color(0xFF3A3A3C))
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineStatusCard(info: OfflineDataInfo) {
    val strings = StringProvider(LocalPlatformContext.current)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusRow(strings["last_update_label"], formatTimestamp(info.lastDownloadTimestamp, strings))
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow(strings["space_used_label"], formatFileSize(info.totalSizeBytes, strings))
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow(
                strings["bus_lines_label"],
                if (info.busLinesCount > 0) strings["bus_lines_value"].replace("%s", info.busLinesCount.toString()) else strings["no_bus_lines_label"]
            )

            if (info.isStale) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = strings["stale_data_warning"],
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings["stale_data_update_recommended"],
                        color = Color(0xFFFF9800),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, color = SecondaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}


@Composable
private fun WavyLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    indicatorColor: Color,
    trackColor: Color,
    barHeight: Dp = 7.dp,
    waveAmplitude: Dp = 3.dp,
    wavelength: Dp = 40.dp,
    waveSpeedMillis: Int = 1000,
    gapWidth: Dp = 5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = waveSpeedMillis, easing = LinearEasing)
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier.height(barHeight + waveAmplitude * 2)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val halfHeight = barHeight.toPx() / 2f

        val progressWidth = (width * progress.coerceIn(0f, 1f))
        val gapPx = gapWidth.toPx()

        val rigidStart = progressWidth + gapPx

        if (rigidStart < width) {
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(rigidStart, centerY - halfHeight),
                size = Size(width - rigidStart, barHeight.toPx()),
                cornerRadius = CornerRadius(halfHeight, halfHeight)
            )
        }

        if (progressWidth > 0f) {
            val ampPx = waveAmplitude.toPx()
            val lenPx = wavelength.toPx()
            val phasePx = phase * lenPx

            val path = Path()

            var x = 0f
            path.moveTo(
                0f,
                centerY - halfHeight + sin((phasePx) / lenPx * 2 * PI).toFloat() * ampPx
            )

            val step = 5f
            while (x <= progressWidth) {
                val y = centerY - halfHeight + sin((x + phasePx) / lenPx * 2 * PI).toFloat() * ampPx
                path.lineTo(x, y)
                x += step
            }

            path.lineTo(
                progressWidth,
                centerY + halfHeight + sin((progressWidth + phasePx) / lenPx * 2 * PI).toFloat() * ampPx
            )

            x = progressWidth
            while (x >= 0f) {
                val y = centerY + halfHeight + sin((x + phasePx) / lenPx * 2 * PI).toFloat() * ampPx
                path.lineTo(x, y)
                x -= step
            }

            path.close()
            drawPath(path, indicatorColor)
        }
    }
}

private val FRENCH_MONTHS = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc."
)

private val ENGLISH_MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Formats an epoch-millis timestamp as e.g. "5 juin 2026 à 14:30" (was Android SimpleDateFormat).
 */
@Composable
private fun formatTimestamp(timestamp: Long, strings: StringProvider): String {
    if (timestamp == 0L) return strings["never"]
    val dt = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val isFrench = strings["never"] == "Jamais"
    val months = if (isFrench) FRENCH_MONTHS else ENGLISH_MONTHS
    val month = months.getOrElse(dt.monthNumber - 1) { "" }
    val hh = dt.hour.toString().padStart(2, '0')
    val mm = dt.minute.toString().padStart(2, '0')
    val dateStr = "${dt.dayOfMonth} $month ${dt.year}"
    val timeStr = "$hh:$mm"
    return strings["date_time_at"]
        .replace("%1\$s", dateStr)
        .replace("%2\$s", timeStr)
}

/**
 * Human-readable byte size in French or English units (was Android Formatter.formatFileSize, which is
 * locale + Context-bound). Approximate (binary 1024 steps, one decimal from Mo up).
 */
@Composable
private fun formatFileSize(bytes: Long, strings: StringProvider): String {
    val isFrench = strings["never"] == "Jamais"
    if (bytes <= 0) return if (isFrench) "0 o" else "0 B"
    val units = if (isFrench) {
        listOf("o", "Ko", "Mo", "Go", "To")
    } else {
        listOf("B", "KB", "MB", "GB", "TB")
    }
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024.0 && i < units.size - 1) {
        value /= 1024.0
        i++
    }
    return if (i == 0) {
        "${value.toLong()} ${units[i]}"
    } else {
        val whole = value.toLong()
        val decimal = ((value - whole) * 10).toLong()
        if (isFrench) {
            "$whole,$decimal ${units[i]}"
        } else {
            "$whole.$decimal ${units[i]}"
        }
    }
}

@Composable
private fun translateOfflineStepDescription(step: String, strings: StringProvider): String {
    return when {
        step == "Téléchargement des données..." -> strings["offline_status_downloading_data"]
        step == "Sauvegarde des données..." -> strings["offline_status_saving_data"]
        step == "Lignes de bus..." -> strings["offline_status_bus_lines"]
        step.startsWith("Lignes de bus (") && step.endsWith(")...") -> {
            val progress = step.substringAfter("Lignes de bus (").substringBefore(")...")
            strings["offline_status_bus_lines_progress"].replace("%s", progress)
        }
        step.startsWith("Tuiles ") && step.endsWith(")...") -> {
            val body = step.removePrefix("Tuiles ").removeSuffix(")...")
            val styleName = body.substringBeforeLast(" (")
            val progress = body.substringAfterLast(" (")
            val localizedStyleName = when (styleName) {
                "Clair" -> strings["theme_light"]
                "Sombre" -> strings["theme_dark"]
                else -> styleName
            }
            strings["offline_status_map_tiles"]
                .replace("%1\$s", localizedStyleName)
                .replace("%2\$s", progress)
        }
        else -> step
    }
}

@Composable
private fun translateOfflineErrorMessage(message: String, strings: StringProvider): String {
    return when {
        message == "Mémoire insuffisante pour télécharger les lignes de bus" -> {
            strings["offline_error_insufficient_memory"]
        }
        message.startsWith("Échec du téléchargement: ") -> {
            val err = message.removePrefix("Échec du téléchargement: ")
            strings["offline_error_download_failed"].replace("%s", err)
        }
        message.startsWith("Échec du téléchargement des lignes de bus: ") -> {
            val err = message.removePrefix("Échec du téléchargement des lignes de bus: ")
            strings["offline_error_bus_lines_failed"].replace("%s", err)
        }
        message.startsWith("Erreur inattendue: ") -> {
            val err = message.removePrefix("Erreur inattendue: ")
            strings["offline_error_unexpected"].replace("%s", err)
        }
        else -> message
    }
}
