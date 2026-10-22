/*
 * Styles, palettes and theme of the kit. Palettes: one per Touhou Project character, light and dark.
 */
package com.patchouli.app.ui.kit

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.patchouli.app.R
import kotlin.math.max
import kotlin.math.min

@Immutable
data class PersonalityColors(
	val primary: Color,
	val onPrimary: Color,
	val secondary: Color,
	val onSecondary: Color,
	val gold: Color,
	val onGold: Color,
	val background: Color,
	val onBackground: Color,
	val surface: Color,
	val onSurface: Color,
	val surfaceVariant: Color,
	val onSurfaceVariant: Color,
	/** The pen's line around every panel: the ink itself. */
	val outline: Color,
	val outlineVariant: Color,
	/** Rules inside a panel: the ink, lighter. */
	val divider: Color,
	val error: Color,
	val onError: Color,
	/** The hard block behind each panel, offset like a badly registered print. */
	val shadow: Color,
	/** Strength of the tone sheets (corners, fills) and of the grid paper behind the page. */
	val screentoneOpacity: Float,
	val gridOpacity: Float,
) {
	val isDark: Boolean get() = background.luminance() < 0.5f
}

/** [border] is the panel's line, [borderButton] and [borderChip] the thinner lines of the small things. */
@Immutable
data class PersonalityShape(val border: Dp, val borderButton: Dp, val borderChip: Dp, val corner: Dp, val cornerSmall: Dp, val pill: Boolean)

@Immutable
data class PersonalityType(
	val display: TextStyle,
	val title: TextStyle,
	val stamp: TextStyle,
	val overline: TextStyle,
	val body: TextStyle,
	val label: TextStyle,
	val mono: TextStyle,
	val uppercaseHeadings: Boolean,
)

/** How far the ink block sits behind each kind of panel: the offset of the hard shadow, no blur. */
@Immutable
data class PersonalityShadow(val card: Dp, val raised: Dp, val modal: Dp, val button: Dp)

@Immutable
data class StatusColors(val ready: Color, val warning: Color, val error: Color)

enum class HeadlineMarker { None, Stamp }

/** Background pattern of the app. */
enum class Motif { None, Grid }

/** Everything that makes one app look like itself; the other app changes only this. */
@Immutable
data class KitStyle(
	val type: PersonalityType,
	val shape: PersonalityShape,
	val shadow: PersonalityShadow,
	val marker: HeadlineMarker,
	val motif: Motif,
)

// ---------------------------------------------------------------- character themes

/** A full palette: paper (page, panel, well), ink, muted ink and three accents + error. */
@Immutable
data class Palette(
	val page: Long, val panel: Long, val well: Long, val ink: Long, val muted: Long,
	val primary: Long, val onPrimary: Long,
	val secondary: Long, val onSecondary: Long,
	val gold: Long, val onGold: Long,
	val error: Long, val onError: Long,
)

data class CharacterTheme(val id: String, val name: String, val light: Palette, val dark: Palette)

object Themes {
	val all: List<CharacterTheme> = listOf(
		CharacterTheme(
			"patchouli", "Patchouli",
			Palette(0xFFF4EEF6, 0xFFFBF8FC, 0xFFEADFEE, 0xFF2B1F35, 0xFF6F6178, 0xFF7C4FB0, 0xFFFFFFFF, 0xFFF0B7D2, 0xFF2B1F35, 0xFFE3B341, 0xFF2B1F35, 0xFFC4304A, 0xFFFFFFFF),
			Palette(0xFF140F1B, 0xFF1D1626, 0xFF2A2035, 0xFFF2EAF6, 0xFFA394B0, 0xFFB993E6, 0xFF1A1024, 0xFFE8A6C8, 0xFF1A1024, 0xFFE6B94A, 0xFF1A1024, 0xFFEB7A8C, 0xFF1A1024),
		),
		CharacterTheme(
			"reimu", "Reimu",
			Palette(0xFFF7EFEE, 0xFFFCF7F6, 0xFFEFDFDE, 0xFF2B1B1C, 0xFF745E60, 0xFFC62B3C, 0xFFFFFFFF, 0xFFF5C6CB, 0xFF2B1B1C, 0xFFE0B33A, 0xFF2B1B1C, 0xFF6E1520, 0xFFFFFFFF),
			Palette(0xFF160E10, 0xFF1F1417, 0xFF2B1B1F, 0xFFF3E9E9, 0xFFA89094, 0xFFF07A86, 0xFF1A0D0F, 0xFFF3C0C7, 0xFF1A0D0F, 0xFFE8C15A, 0xFF1A0D0F, 0xFFFFB4A8, 0xFF1A0D0F),
		),
		CharacterTheme(
			"marisa", "Marisa",
			Palette(0xFFF2F0EC, 0xFFFAF8F5, 0xFFE6E2DB, 0xFF1A1814, 0xFF66625A, 0xFF1A1814, 0xFFFAF8F5, 0xFFEBD48A, 0xFF1A1814, 0xFFD9A62E, 0xFF1A1814, 0xFFC43C3C, 0xFFFFFFFF),
			Palette(0xFF0F0E0C, 0xFF171613, 0xFF22201B, 0xFFF0ECE3, 0xFF9C968A, 0xFFF0ECE3, 0xFF0F0E0C, 0xFFE6C96B, 0xFF0F0E0C, 0xFFF1CB4F, 0xFF0F0E0C, 0xFFF07B7B, 0xFF0F0E0C),
		),
		CharacterTheme(
			"sakuya", "Sakuya",
			Palette(0xFFEFF2F6, 0xFFF8FAFC, 0xFFDFE5EE, 0xFF1B2230, 0xFF606A7A, 0xFF3E6BB0, 0xFFFFFFFF, 0xFFCFDBEA, 0xFF1B2230, 0xFF3F8A50, 0xFFFFFFFF, 0xFFC0392B, 0xFFFFFFFF),
			Palette(0xFF0F1218, 0xFF161B23, 0xFF202732, 0xFFE9EEF5, 0xFF96A0AE, 0xFF8FB3EA, 0xFF0F1218, 0xFFC3D2E6, 0xFF0F1218, 0xFF8BD19A, 0xFF0F1218, 0xFFF08080, 0xFF0F1218),
		),
		CharacterTheme(
			"remilia", "Remilia",
			Palette(0xFFF8EEF1, 0xFFFCF6F8, 0xFFF0DCE3, 0xFF2E1620, 0xFF76606A, 0xFFC8283F, 0xFFFFFFFF, 0xFFF4C1D1, 0xFF2E1620, 0xFFB9AEE0, 0xFF2E1620, 0xFF6E1520, 0xFFFFFFFF),
			Palette(0xFF160D12, 0xFF1F141A, 0xFF2B1B23, 0xFFF5E8EE, 0xFFA8929C, 0xFFF27A8A, 0xFF1A0C10, 0xFFF3B9CB, 0xFF1A0C10, 0xFFC8BDF0, 0xFF1A0C10, 0xFFFFA9A0, 0xFF1A0C10),
		),
		CharacterTheme(
			"flandre", "Flandre",
			Palette(0xFFF9F1EC, 0xFFFDF8F4, 0xFFF1E1D6, 0xFF2B1A14, 0xFF745F55, 0xFFD1342F, 0xFFFFFFFF, 0xFFF6D46B, 0xFF2B1A14, 0xFF5BC4D9, 0xFF2B1A14, 0xFF6E1520, 0xFFFFFFFF),
			Palette(0xFF170F0D, 0xFF201512, 0xFF2C1D19, 0xFFF5EBE6, 0xFFA8958C, 0xFFF08A7E, 0xFF1A0F0C, 0xFFF3D77A, 0xFF1A0F0C, 0xFF7ADCEE, 0xFF1A0F0C, 0xFFFFB09E, 0xFF1A0F0C),
		),
		CharacterTheme(
			"cirno", "Cirno",
			Palette(0xFFEEF5FA, 0xFFF7FBFE, 0xFFDCEBF5, 0xFF14263A, 0xFF5B6F82, 0xFF2F8FD8, 0xFFFFFFFF, 0xFFCDE8F8, 0xFF14263A, 0xFF9FE0EF, 0xFF14263A, 0xFFD8573A, 0xFFFFFFFF),
			Palette(0xFF0C141C, 0xFF131D27, 0xFF1C2A36, 0xFFE6F1F8, 0xFF90A5B6, 0xFF7FC6F5, 0xFF0C141C, 0xFFBFE3F5, 0xFF0C141C, 0xFFA8ECF7, 0xFF0C141C, 0xFFF59A82, 0xFF0C141C),
		),
		CharacterTheme(
			"youmu", "Youmu",
			Palette(0xFFEFF4EF, 0xFFF8FBF8, 0xFFDEE8DF, 0xFF17251A, 0xFF5E705F, 0xFF3E8A4F, 0xFFFFFFFF, 0xFFCFE8D3, 0xFF17251A, 0xFFC9D2D9, 0xFF17251A, 0xFFC0392B, 0xFFFFFFFF),
			Palette(0xFF0E1510, 0xFF151E17, 0xFF1F2B22, 0xFFE9F2EA, 0xFF93A695, 0xFF86CE93, 0xFF0E1510, 0xFFBEE2C4, 0xFF0E1510, 0xFFD5DEE5, 0xFF0E1510, 0xFFF08080, 0xFF0E1510),
		),
		CharacterTheme(
			"yuyuko", "Yuyuko",
			Palette(0xFFF7EEF4, 0xFFFCF7FA, 0xFFEEDDE8, 0xFF2A1A26, 0xFF705F6B, 0xFFB84E85, 0xFFFFFFFF, 0xFFBFD8F0, 0xFF2A1A26, 0xFFE3C46A, 0xFF2A1A26, 0xFF8A2A4E, 0xFFFFFFFF),
			Palette(0xFF170F15, 0xFF20151D, 0xFF2C1E29, 0xFFF5E9F1, 0xFFA8929F, 0xFFF0A3C6, 0xFF1A0F16, 0xFFB9D6F0, 0xFF1A0F16, 0xFFECCF7A, 0xFF1A0F16, 0xFFFFA5B8, 0xFF1A0F16),
		),
		CharacterTheme(
			"alice", "Alice",
			Palette(0xFFEFF1F8, 0xFFF8F9FD, 0xFFDFE3F1, 0xFF1A2038, 0xFF5F6680, 0xFF3B63C4, 0xFFFFFFFF, 0xFFCBD8F4, 0xFF1A2038, 0xFFE6C25A, 0xFF1A2038, 0xFFC0392B, 0xFFFFFFFF),
			Palette(0xFF0F1119, 0xFF161A25, 0xFF212633, 0xFFEAEDF7, 0xFF959CB3, 0xFF8DA9F0, 0xFF0F1119, 0xFFBFCFF2, 0xFF0F1119, 0xFFEED27A, 0xFF0F1119, 0xFFF08080, 0xFF0F1119),
		),
	)

	const val DEFAULT = "patchouli"

	fun byId(id: String): CharacterTheme = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT }
}

fun Palette.toColors(dark: Boolean): PersonalityColors = PersonalityColors(
	primary = Color(primary), onPrimary = Color(onPrimary),
	secondary = Color(secondary), onSecondary = Color(onSecondary),
	gold = Color(gold), onGold = Color(onGold),
	background = Color(page), onBackground = Color(ink),
	surface = Color(panel), onSurface = Color(ink),
	surfaceVariant = Color(well), onSurfaceVariant = Color(muted),
	outline = Color(ink),
	outlineVariant = Color(muted),
	divider = Color(ink).copy(alpha = 0.4f),
	error = Color(error), onError = Color(onError),
	// day: the block is ink; night: it is black, a step darker than the page
	shadow = if (dark) Color(0xFF000000) else Color(ink),
	screentoneOpacity = if (dark) 0.20f else 0.16f,
	gridOpacity = if (dark) 0.06f else 0.05f,
)

// ---------------------------------------------------------------- type and this app's style

val NotoSansFamily = FontFamily(
	Font(R.font.notosans_regular, FontWeight.Normal),
	Font(R.font.notosans_medium, FontWeight.Medium),
	Font(R.font.notosans_bold, FontWeight.Bold),
	Font(R.font.notosans_black, FontWeight.Black),
)

val JetBrainsMonoFamily = FontFamily(
	Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
	Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
	Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
)

/** Anton ships one weight; without this a bold request smears it. */
private val SingleWeight = FontSynthesis.None

/** The lettering of the headlines: Anton, in capitals, as on a manga cover. */
val AntonFamily = FontFamily(Font(R.font.anton, FontWeight.Normal))

/**
 * Patchouli drawn as a manga, the way Komi Store draws its pages: Anton in capitals for the
 * headlines and stamps, Noto Sans for the reading, JetBrains Mono for the numbers; square panels
 * with a 3 dp line of ink and a hard shadow behind; tone sheets in the corners, grid paper under all.
 */
val EstiloApp = KitStyle(
	type = PersonalityType(
		display = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = 0.01.em, fontSynthesis = SingleWeight),
		title = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 0.01.em, fontSynthesis = SingleWeight),
		stamp = TextStyle(fontFamily = AntonFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 16.sp, letterSpacing = 0.06.em, fontSynthesis = SingleWeight),
		overline = TextStyle(fontFamily = NotoSansFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.em),
		body = TextStyle(fontFamily = NotoSansFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
		label = TextStyle(fontFamily = NotoSansFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
		mono = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontSynthesis = SingleWeight),
		uppercaseHeadings = true,
	),
	shape = PersonalityShape(border = 3.dp, borderButton = 2.5.dp, borderChip = 2.dp, corner = 0.dp, cornerSmall = 0.dp, pill = false),
	shadow = PersonalityShadow(card = 6.dp, raised = 10.dp, modal = 14.dp, button = 4.dp),
	marker = HeadlineMarker.Stamp,
	motif = Motif.Grid,
)

@Immutable
data class KitPersonality(
	val colors: PersonalityColors,
	val type: PersonalityType,
	val shape: PersonalityShape,
	val shadow: PersonalityShadow,
	val headlineMarker: HeadlineMarker,
	val motif: Motif,
)

val LocalPersonality = staticCompositionLocalOf<KitPersonality> {
	error("No Personality provided. Wrap content in the app theme.")
}

val LocalStatusColors = staticCompositionLocalOf { StatusColors(Color(0xFFE3B341), Color(0xFFE3B341), Color(0xFFC4304A)) }

object Spacing {
	val xs: Dp = 4.dp
	val sm: Dp = 8.dp
	val md: Dp = 12.dp
	val lg: Dp = 16.dp
	val xl: Dp = 24.dp
	val xxl: Dp = 32.dp
}

private const val MIN_BODY_CONTRAST = 4.5f

fun contrastRatio(a: Color, b: Color): Float {
	val hi = max(a.luminance(), b.luminance())
	val lo = min(a.luminance(), b.luminance())
	return (hi + 0.05f) / (lo + 0.05f)
}

fun inkOn(background: Color, ink: Color, page: Color): Color =
	if (contrastRatio(background, ink) >= contrastRatio(background, page)) ink else page

fun ensureContrast(preferred: Color, background: Color, ink: Color, page: Color, minRatio: Float = MIN_BODY_CONTRAST): Color =
	if (contrastRatio(preferred, background) >= minRatio) preferred else inkOn(background, ink, page)

private fun PersonalityColors.toMaterialColorScheme(): ColorScheme {
	val base = if (isDark) darkColorScheme() else lightColorScheme()
	return base.copy(
		primary = primary, onPrimary = onPrimary,
		primaryContainer = primary, onPrimaryContainer = onPrimary,
		secondary = secondary, onSecondary = onSecondary,
		background = background, onBackground = onBackground,
		surface = surface, onSurface = onSurface,
		surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
		surfaceContainerLowest = background, surfaceContainerLow = surface,
		surfaceContainer = surface, surfaceContainerHigh = surfaceVariant, surfaceContainerHighest = surfaceVariant,
		outline = outline, outlineVariant = outlineVariant,
		error = error, onError = onError, scrim = shadow,
	)
}

// ---------------------------------------------------------------- persisted choice

/** Theme choice (character + mode) kept in SharedPreferences and observed by Compose. */
object ThemePrefs {
	private const val FILE = "theme"
	private lateinit var context: Context

	var themeId by mutableStateOf(Themes.DEFAULT)
		private set
	var mode by mutableStateOf("system") // system | light | dark
		private set

	fun init(context: Context) {
		this.context = context.applicationContext
		val prefs = this.context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		themeId = prefs.getString("theme_id", Themes.DEFAULT) ?: Themes.DEFAULT
		mode = prefs.getString("mode", "system") ?: "system"
	}

	fun selectTheme(id: String) {
		themeId = id
		context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString("theme_id", id) }
	}

	fun selectMode(value: String) {
		mode = value
		context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString("mode", value) }
	}
}

@Composable
fun PatchouliTheme(content: @Composable () -> Unit) {
	val systemDark = isSystemInDarkTheme()
	val dark = when (ThemePrefs.mode) {
		"light" -> false
		"dark" -> true
		else -> systemDark
	}
	val theme = Themes.byId(ThemePrefs.themeId)
	val palette = if (dark) theme.dark else theme.light
	val personality = remember(palette, dark) {
		KitPersonality(
			colors = palette.toColors(dark),
			type = EstiloApp.type,
			shape = EstiloApp.shape,
			shadow = EstiloApp.shadow,
			headlineMarker = EstiloApp.marker,
			motif = EstiloApp.motif,
		)
	}
	val scheme = remember(personality) { personality.colors.toMaterialColorScheme() }
	val status = remember(personality) { StatusColors(personality.colors.gold, personality.colors.gold, personality.colors.error) }
	CompositionLocalProvider(
		LocalPersonality provides personality,
		LocalStatusColors provides status,
		LocalContentColor provides personality.colors.onBackground,
	) {
		MaterialTheme(colorScheme = scheme, content = content)
	}
}