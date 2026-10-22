/*
 * Components of the kit: KitText, KitHeadline, KitSurface, KitButton, KitIconButton, KitChip,
 * KitBadge, KitCheckbox, KitTextField, KitHorizontalDivider, KitTopBar, KitBottomBar, KitScaffold.
 * The manga look of Komi Store (kurikomi-labs/komi-store, Apache 2.0) carried over to Patchouli:
 * square panels with the pen's line and a hard ink shadow, stamps that sink when pressed, tone sheets.
 */
package com.patchouli.app.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.patchouli.app.R
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.tan

// ---------------------------------------------------------------- text

/** Display, Title and Stamp are the Anton lettering, in capitals; Overline is always in capitals; the rest as written. */
enum class KitTextRole { Display, Title, Stamp, Overline, Label, Body, Mono }

private fun kitTextStyle(
	type: PersonalityType, role: KitTextRole,
	fontSize: TextUnit, fontWeight: FontWeight?, letterSpacing: TextUnit, lineHeight: TextUnit,
): TextStyle {
	var style = when (role) {
		KitTextRole.Display -> type.display
		KitTextRole.Title -> type.title
		KitTextRole.Stamp -> type.stamp
		KitTextRole.Overline -> type.overline
		KitTextRole.Body -> type.body
		KitTextRole.Label -> type.label
		KitTextRole.Mono -> type.mono
	}
	if (fontSize.isSpecified) style = style.copy(fontSize = fontSize)
	if (fontWeight != null) style = style.copy(fontWeight = fontWeight)
	if (letterSpacing.isSpecified) style = style.copy(letterSpacing = letterSpacing)
	if (lineHeight.isSpecified) style = style.copy(lineHeight = lineHeight)
	return style
}

private fun KitTextRole.inCapitals(type: PersonalityType): Boolean =
	when (this) {
		KitTextRole.Overline -> true
		KitTextRole.Display, KitTextRole.Title, KitTextRole.Stamp -> type.uppercaseHeadings
		else -> false
	}

@Composable
fun KitText(
	text: String,
	modifier: Modifier = Modifier,
	role: KitTextRole = KitTextRole.Body,
	color: Color = Color.Unspecified,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
	textAlign: TextAlign? = null,
	uppercase: Boolean? = null,
	fontSize: TextUnit = TextUnit.Unspecified,
	fontWeight: FontWeight? = null,
	letterSpacing: TextUnit = TextUnit.Unspecified,
	lineHeight: TextUnit = TextUnit.Unspecified,
) {
	val personality = LocalPersonality.current
	val style = kitTextStyle(personality.type, role, fontSize, fontWeight, letterSpacing, lineHeight)
	val resolvedUppercase = uppercase ?: role.inCapitals(personality.type)
	Text(
		text = if (resolvedUppercase) text.uppercase() else text,
		modifier = modifier,
		color = if (color != Color.Unspecified) color else personality.colors.onSurface,
		maxLines = maxLines,
		overflow = overflow,
		textAlign = textAlign,
		style = style,
	)
}

@Composable
fun KitText(
	text: AnnotatedString,
	modifier: Modifier = Modifier,
	role: KitTextRole = KitTextRole.Body,
	color: Color = Color.Unspecified,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
	textAlign: TextAlign? = null,
	fontSize: TextUnit = TextUnit.Unspecified,
	fontWeight: FontWeight? = null,
	letterSpacing: TextUnit = TextUnit.Unspecified,
	lineHeight: TextUnit = TextUnit.Unspecified,
) {
	val personality = LocalPersonality.current
	val style = kitTextStyle(personality.type, role, fontSize, fontWeight, letterSpacing, lineHeight)
	Text(
		text = text,
		modifier = modifier,
		color = if (color != Color.Unspecified) color else personality.colors.onSurface,
		maxLines = maxLines,
		overflow = overflow,
		textAlign = textAlign,
		style = style,
	)
}

/** The headline marker: a skewed stamp in the accent with the pen around it; [size] is its width, it stands almost twice as tall. */
@Composable
fun HeadlineMark(size: Dp = 12.dp) {
	val colors = LocalPersonality.current.colors
	Box(
		Modifier
			.size(width = size, height = size * 11 / 6)
			.background(colors.primary, SkewStampShape)
			.border(2.dp, colors.outline, SkewStampShape),
	)
}

@Composable
fun KitHeadline(
	text: String,
	modifier: Modifier = Modifier,
	role: KitTextRole = KitTextRole.Title,
	color: Color = Color.Unspecified,
	maxLines: Int = 2,
) {
	val personality = LocalPersonality.current
	if (personality.headlineMarker == HeadlineMarker.None) {
		KitText(text, modifier, role, color, maxLines, TextOverflow.Ellipsis)
		return
	}
	Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
		HeadlineMark()
		KitText(text = text, role = role, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
	}
}

// ---------------------------------------------------------------- surfaces

enum class KitSurfaceElevation { Flat, Card, Raised, Modal }
enum class KitSurfacePaper { Surface, Background }
enum class KitScreentone { None, Corner, Fill }

private data class PanelDepth(val shadow: Dp, val border: Dp)

private fun panelDepth(elevation: KitSurfaceElevation, shadow: PersonalityShadow, border: Dp): PanelDepth =
	when (elevation) {
		KitSurfaceElevation.Flat -> PanelDepth(0.dp, border)
		KitSurfaceElevation.Card -> PanelDepth(shadow.card, border)
		KitSurfaceElevation.Raised -> PanelDepth(shadow.raised, border)
		KitSurfaceElevation.Modal -> PanelDepth(shadow.modal, border + 1.dp)
	}

/**
 * A panel of the page: paper, the pen's 3 dp line around it and a hard ink shadow behind, as deep
 * as its elevation; a press lands it on the shadow. [tilt] leans the whole panel a little.
 */
@Composable
fun KitSurface(
	modifier: Modifier = Modifier,
	elevation: KitSurfaceElevation = KitSurfaceElevation.Card,
	paper: KitSurfacePaper = KitSurfacePaper.Surface,
	screentone: KitScreentone = KitScreentone.None,
	screentoneBoost: Float = 1f,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	tilt: Float = 0f,
	topEdgeOnly: Boolean = false,
	borderColor: Color = Color.Unspecified,
	contentPadding: PaddingValues = PaddingValues(0.dp),
	content: @Composable () -> Unit,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val depth = panelDepth(elevation, personality.shadow, personality.shape.border)
	val borderWidth = if (topEdgeOnly) maxOf(depth.border, 4.dp) else depth.border
	val shape = remember(personality.shape.corner) { kitShape(personality.shape.corner) }
	val needsClip = personality.shape.corner > 0.dp
	val fill = if (paper == KitSurfacePaper.Surface) colors.surface else colors.background
	val contentInk = if (paper == KitSurfacePaper.Surface) colors.onSurface else colors.onBackground
	val line = if (borderColor != Color.Unspecified) borderColor else colors.outline
	val pressable = onClick != null || onLongClick != null

	val depthModifier = if (pressable) {
		val interaction = remember { MutableInteractionSource() }
		val pressed by interaction.collectIsPressedAsState()
		val focused by interaction.collectIsFocusedAsState()
		val press = animateFloatAsState(if (pressed) 1f else 0f, label = "kitSurfacePress")
		Modifier
			.inkFocusRing(focused = { focused }, color = colors.primary)
			.inkPress(pressProgress = { press.value }, shadow = DpOffset(depth.shadow, depth.shadow), shadowColor = colors.shadow, shape = shape, pressInset = 2.dp)
			.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick ?: {}, onLongClick = onLongClick)
	} else if (depth.shadow > 0.dp) {
		Modifier.hardShadow(offset = DpOffset(depth.shadow, depth.shadow), color = colors.shadow, shape = shape)
	} else {
		Modifier
	}
	val toneModifier = when (screentone) {
		KitScreentone.None -> Modifier
		KitScreentone.Corner -> Modifier.screentoneCorner(contentInk, colors.screentoneOpacity, boost = screentoneBoost)
		KitScreentone.Fill -> Modifier.screentoneFill(contentInk, colors.screentoneOpacity)
	}
	val borderModifier = if (topEdgeOnly) {
		Modifier.drawBehind { drawRect(color = line, topLeft = Offset.Zero, size = Size(size.width, borderWidth.toPx())) }
	} else {
		Modifier.border(width = borderWidth, color = line, shape = shape)
	}

	Box(
		modifier = modifier
			.then(if (tilt != 0f) Modifier.rotate(tilt) else Modifier)
			.then(depthModifier)
			.then(if (needsClip) Modifier.clip(shape) else Modifier)
			.background(color = fill, shape = shape)
			.then(toneModifier)
			.then(borderModifier)
			.padding(contentPadding),
	) {
		CompositionLocalProvider(LocalContentColor provides contentInk) { content() }
	}
}

// ---------------------------------------------------------------- buttons

enum class KitButtonVariant { Primary, Tonal, Outline, Text, Destructive }
enum class KitButtonSize { Sm, Md, Lg }

private data class ButtonMetrics(val height: Dp, val hPadding: Dp, val font: TextUnit, val icon: Dp, val gap: Dp, val shadow: Dp, val border: Dp)

private fun buttonMetrics(size: KitButtonSize): ButtonMetrics =
	when (size) {
		KitButtonSize.Sm -> ButtonMetrics(34.dp, 14.dp, 12.sp, 15.dp, 7.dp, 3.dp, 1.5.dp)
		KitButtonSize.Md -> ButtonMetrics(44.dp, 20.dp, 14.sp, 18.dp, 9.dp, 4.dp, 2.5.dp)
		KitButtonSize.Lg -> ButtonMetrics(54.dp, 26.dp, 16.sp, 22.dp, 10.dp, 5.dp, 3.dp)
	}

internal fun kitButtonContainer(variant: KitButtonVariant, colors: PersonalityColors): Color =
	when (variant) {
		KitButtonVariant.Primary -> colors.primary
		KitButtonVariant.Destructive -> colors.error
		KitButtonVariant.Tonal -> colors.surfaceVariant
		KitButtonVariant.Outline, KitButtonVariant.Text -> Color.Transparent
	}

internal fun kitButtonContent(variant: KitButtonVariant, colors: PersonalityColors, ambient: Color): Color =
	when (variant) {
		KitButtonVariant.Primary -> ensureContrast(colors.onPrimary, colors.primary, colors.onBackground, colors.background)
		KitButtonVariant.Destructive -> ensureContrast(colors.onError, colors.error, colors.onBackground, colors.background)
		KitButtonVariant.Tonal -> ensureContrast(colors.onSurface, colors.surfaceVariant, colors.onBackground, colors.background)
		KitButtonVariant.Outline, KitButtonVariant.Text -> ambient
	}

/**
 * A button is a stamp: the pen around it and a hard shadow it sinks onto. Outline and Text have
 * no block (Outline keeps the line in its own ink); [emphasized] adds speed lines to a filled one.
 */
@Composable
fun KitButton(
	onClick: () -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	variant: KitButtonVariant = KitButtonVariant.Primary,
	size: KitButtonSize = KitButtonSize.Md,
	enabled: Boolean = true,
	emphasized: Boolean = false,
	fullWidth: Boolean = false,
	leadingIcon: ImageVector? = null,
	trailingIcon: ImageVector? = null,
	container: Color = Color.Unspecified,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val metrics = buttonMetrics(size)
	val shape = remember(personality.shape.cornerSmall) { kitShape(personality.shape.cornerSmall) }
	val alpha = if (enabled) 1f else 0.45f
	val flat = variant == KitButtonVariant.Text
	val ambientInk = LocalContentColor.current
	val overridden = container != Color.Unspecified
	val containerColor = if (overridden) container else kitButtonContainer(variant, colors)
	val contentColor = if (overridden) ensureContrast(colors.onPrimary, container, colors.onBackground, colors.background) else kitButtonContent(variant, colors, ambientInk)
	val borderColor = if (overridden || containerColor == Color.Transparent) contentColor else colors.outline
	val stamped = !flat && containerColor != Color.Transparent
	val sweep = emphasized && enabled && (variant == KitButtonVariant.Primary || variant == KitButtonVariant.Destructive)
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val focused by interaction.collectIsFocusedAsState()
	val press = animateFloatAsState(if (pressed && enabled) 1f else 0f, label = "kitButtonPress")
	val pressModifier = if (!stamped) {
		Modifier.offset { IntOffset(0, (1.dp.toPx() * press.value).roundToInt()) }
	} else {
		Modifier.inkPress(pressProgress = { press.value }, shadow = DpOffset(metrics.shadow, metrics.shadow), shadowColor = colors.shadow.copy(alpha = alpha), shape = shape)
	}
	Row(
		modifier = modifier
			.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
			.inkFocusRing(focused = { focused }, color = if (overridden) contentColor else colors.primary)
			.then(pressModifier)
			.then(if (personality.shape.cornerSmall > 0.dp || sweep) Modifier.clip(shape) else Modifier)
			.background(color = containerColor.copy(alpha = containerColor.alpha * alpha), shape = shape)
			.then(if (sweep) Modifier.speedLines(color = contentColor, opacity = 0.16f) else Modifier)
			.then(if (!flat) Modifier.border(width = metrics.border, color = borderColor.copy(alpha = borderColor.alpha * alpha), shape = shape) else Modifier)
			.clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
			.height(metrics.height)
			.padding(horizontal = if (flat) metrics.hPadding / 2 else metrics.hPadding),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment = Alignment.CenterVertically,
	) {
		CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = alpha)) {
			if (leadingIcon != null) {
				Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon))
				Spacer(Modifier.width(metrics.gap))
			}
			KitText(label, role = KitTextRole.Label, color = contentColor.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = metrics.font)
			if (trailingIcon != null) {
				Spacer(Modifier.width(metrics.gap))
				Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon))
			}
		}
	}
}

/** A square stamp with a glyph in it; [size] is the box, the tap target is at least 44 dp. */
@Composable
fun KitIconButton(
	icon: ImageVector,
	contentDescription: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	variant: KitButtonVariant = KitButtonVariant.Tonal,
	enabled: Boolean = true,
	size: Dp = 42.dp,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val shape = remember(personality.shape.cornerSmall) { kitShape(personality.shape.cornerSmall) }
	val small = size < 40.dp
	val shadow = if (small) 3.dp else 4.dp
	val borderWidth = if (small) 1.5.dp else 2.5.dp
	val iconSize = if (small) 18.dp else 20.dp
	val alpha = if (enabled) 1f else 0.45f
	val flat = variant == KitButtonVariant.Text
	val container = kitButtonContainer(variant, colors)
	val contentColor = kitButtonContent(variant, colors, LocalContentColor.current)
	val borderColor = if (container == Color.Transparent) contentColor else colors.outline
	val stamped = !flat && container != Color.Transparent
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val focused by interaction.collectIsFocusedAsState()
	val press = animateFloatAsState(if (pressed && enabled) 1f else 0f, label = "kitIconButtonPress")
	val pressModifier = if (!stamped) {
		Modifier.offset { IntOffset(0, (1.dp.toPx() * press.value).roundToInt()) }
	} else {
		Modifier.inkPress(pressProgress = { press.value }, shadow = DpOffset(shadow, shadow), shadowColor = colors.shadow.copy(alpha = alpha), shape = shape)
	}
	Box(
		modifier = modifier
			.size(maxOf(size, 44.dp))
			.clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.inkFocusRing(focused = { focused }, color = colors.primary)
				.then(pressModifier)
				.size(size)
				.background(color = container.copy(alpha = container.alpha * alpha), shape = shape)
				.then(if (!flat) Modifier.border(width = borderWidth, color = borderColor.copy(alpha = alpha), shape = shape) else Modifier),
			contentAlignment = Alignment.Center,
		) {
			Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize), tint = contentColor.copy(alpha = alpha))
		}
	}
}

// ---------------------------------------------------------------- chips and badges

enum class KitChipKind { Info, Filter }

private data class ChipMetrics(val height: Dp, val paddingX: Dp, val icon: Dp, val border: Dp)

private fun chipMetrics(kind: KitChipKind, small: Boolean): ChipMetrics =
	when {
		kind == KitChipKind.Filter && small -> ChipMetrics(26.dp, 11.dp, 14.dp, 2.5.dp)
		kind == KitChipKind.Filter -> ChipMetrics(34.dp, 14.dp, 16.dp, 2.5.dp)
		small -> ChipMetrics(24.dp, 9.dp, 13.dp, 2.dp)
		else -> ChipMetrics(28.dp, 10.dp, 14.dp, 2.dp)
	}

private const val CHIP_SKEW_DEGREES = 8f

private fun chipStampShape(skewDegrees: Float): Shape =
	GenericShape { size, _ ->
		val sk = size.height * tan(skewDegrees * (PI / 180.0)).toFloat()
		moveTo(sk, 0f)
		lineTo(size.width, 0f)
		lineTo(size.width - sk, size.height)
		lineTo(0f, size.height)
		close()
	}

/**
 * A chip: an Info chip is a rectangle with the pen around it; a Filter chip is a skewed stamp in
 * Anton, leaning [index]-wise one way or the other, with a block behind it when selected.
 * `fill`/`ink` give it a semantic colour.
 */
@Composable
fun KitChip(
	label: String,
	modifier: Modifier = Modifier,
	kind: KitChipKind = KitChipKind.Info,
	small: Boolean = false,
	selected: Boolean = false,
	index: Int = 0,
	tilt: Boolean = true,
	leadingIcon: ImageVector? = null,
	fill: Color = Color.Unspecified,
	ink: Color = Color.Unspecified,
	onClick: (() -> Unit)? = null,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val metrics = chipMetrics(kind, small)
	val isFilter = kind == KitChipKind.Filter
	val tappable = onClick != null || isFilter
	val doSkew = isFilter && tilt
	val rot = if (doSkew) (if (index % 2 != 0) 0.4f else -0.4f) else 0f
	val bg = when {
		fill != Color.Unspecified -> fill
		selected -> colors.primary
		isFilter -> colors.surface
		else -> colors.background
	}
	val fg = when {
		ink != Color.Unspecified -> ink
		fill != Color.Unspecified -> inkOn(fill, colors.onBackground, colors.background)
		selected -> colors.onPrimary
		else -> colors.onSurface
	}
	val hasShadow = selected && isFilter
	val shape = remember(doSkew) { if (doSkew) chipStampShape(CHIP_SKEW_DEGREES) else RectangleShape }
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val focused by interaction.collectIsFocusedAsState()
	val press = animateFloatAsState(if (pressed) 1f else 0f, label = "kitChipPress")
	Row(
		modifier = modifier
			.then(if (rot != 0f) Modifier.rotate(rot) else Modifier)
			.then(if (tappable) Modifier.inkFocusRing(focused = { focused }, color = colors.primary) else Modifier)
			.offset { IntOffset(0, (1.dp.toPx() * press.value).roundToInt()) }
			.then(if (hasShadow) Modifier.hardShadow(offset = DpOffset(2.5.dp, 2.5.dp), color = colors.shadow, shape = shape) else Modifier)
			.background(color = bg, shape = shape)
			.border(width = metrics.border, color = colors.outline, shape = shape)
			.then(if (tappable) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick ?: {}) else Modifier)
			.height(metrics.height)
			.padding(horizontal = metrics.paddingX),
		horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon), tint = fg)
		KitText(text = label, role = if (isFilter) KitTextRole.Stamp else KitTextRole.Label, color = fg, maxLines = 1, overflow = TextOverflow.Clip, fontSize = if (isFilter) TextUnit.Unspecified else 12.sp)
	}
}

enum class KitBadgeTone { Alert, Neutral }

/** A small stamp with a number: the pen, a block behind, leaning when [tilt]. */
@Composable
fun KitBadge(
	text: String,
	modifier: Modifier = Modifier,
	tone: KitBadgeTone = KitBadgeTone.Neutral,
	tilt: Boolean = false,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val fill = if (tone == KitBadgeTone.Neutral) colors.primary else colors.error
	val fg = if (tone == KitBadgeTone.Neutral) colors.onPrimary else colors.onError
	Box(
		modifier = modifier
			.then(if (tilt) Modifier.rotate(-4f) else Modifier)
			.height(20.dp).widthIn(min = 20.dp)
			.hardShadow(DpOffset(2.dp, 2.dp), colors.shadow)
			.background(fill)
			.border(2.dp, colors.outline)
			.padding(horizontal = 5.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(text = text, color = fg, style = personality.type.label.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"))
	}
}

// ---------------------------------------------------------------- inputs

/** A square box drawn with the pen, filled with the accent when ticked. */
@Composable
fun KitCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val alpha = if (enabled) 1f else 0.45f
	val shape = kitShape(personality.shape.cornerSmall)
	val fill by animateColorAsState(if (checked) colors.primary else colors.surface, label = "kitCheckboxFill")
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	val toggleModifier =
		if (onCheckedChange != null) {
			Modifier.toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
		} else Modifier
	Box(
		modifier = modifier
			.then(toggleModifier)
			.size(22.dp)
			.inkFocusRing(focused = { focused }, color = colors.primary)
			.background(color = fill.copy(alpha = fill.alpha * alpha), shape = shape)
			.border(width = 2.5.dp, color = colors.outline.copy(alpha = alpha), shape = shape),
		contentAlignment = Alignment.Center,
	) {
		if (checked) Icon(ImageVector.vectorResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.onPrimary.copy(alpha = alpha))
	}
}

/** A field is a panel too: the pen around it, a block behind that turns to the accent while it has the focus. */
@Composable
fun KitTextField(
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier,
	label: String? = null,
	helper: String? = null,
	placeholder: String? = null,
	keyboardType: KeyboardType = KeyboardType.Text,
	enabled: Boolean = true,
	multiline: Boolean = false,
	rows: Int = 4,
	onCommit: (() -> Unit)? = null,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val type = personality.type
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	val focusRequester = remember { FocusRequester() }
	val shellInteraction = remember { MutableInteractionSource() }
	val shadowColor = if (focused) colors.primary else colors.shadow
	val fill = if (enabled) colors.surface else colors.surfaceVariant
	val reading = type.body.copy(fontSize = 16.sp, lineHeight = 24.sp)
	Column(modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(0.5f))) {
		if (label != null) {
			KitText(text = label, role = KitTextRole.Overline, color = colors.onSurface)
			Spacer(Modifier.height(7.dp))
		}
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.then(if (enabled) Modifier.hardShadow(offset = DpOffset(4.dp, 4.dp), color = shadowColor, shape = RectangleShape) else Modifier)
				.background(color = fill, shape = RectangleShape)
				.border(width = personality.shape.border, color = colors.outline, shape = RectangleShape)
				.then(if (enabled) Modifier.clickable(interactionSource = shellInteraction, indication = null, onClick = { focusRequester.requestFocus() }) else Modifier)
				.then(if (multiline) Modifier.padding(10.dp) else Modifier.heightIn(min = 46.dp).padding(horizontal = 12.dp)),
			verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(9.dp),
		) {
			BasicTextField(
				value = value,
				onValueChange = onValueChange,
				modifier = Modifier.weight(1f).focusRequester(focusRequester),
				enabled = enabled,
				textStyle = reading.copy(color = colors.onSurface),
				cursorBrush = SolidColor(colors.primary),
				singleLine = !multiline,
				minLines = if (multiline) rows else 1,
				maxLines = if (multiline) Int.MAX_VALUE else 1,
				keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (!multiline && onCommit != null) ImeAction.Done else ImeAction.Default),
				keyboardActions = KeyboardActions(onDone = { onCommit?.invoke() }),
				interactionSource = interaction,
				decorationBox = { inner ->
					if (value.isEmpty() && placeholder != null) {
						Text(text = placeholder, style = reading, color = colors.onSurfaceVariant, maxLines = if (multiline) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
					}
					inner()
				},
			)
		}
		if (helper != null) {
			Spacer(Modifier.height(6.dp))
			Text(text = helper, style = type.body.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.onSurfaceVariant)
		}
	}
}

/** A rule of ink, 2 dp unless told otherwise. */
@Composable
fun KitHorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 2.dp, color: Color = Color.Unspecified) {
	val colors = LocalPersonality.current.colors
	val resolved = if (color != Color.Unspecified) color else colors.outline
	Box(modifier.fillMaxWidth().height(thickness).background(resolved))
}

// ---------------------------------------------------------------- bars and scaffold

private fun accentTitle(title: String, accentSub: String?, accent: Color): AnnotatedString {
	if (accentSub.isNullOrEmpty()) return AnnotatedString(title)
	val idx = title.indexOf(accentSub, ignoreCase = true)
	if (idx < 0) return AnnotatedString(title)
	return buildAnnotatedString {
		append(title.substring(0, idx))
		withStyle(SpanStyle(color = accent)) { append(title.substring(idx, idx + accentSub.length)) }
		append(title.substring(idx + accentSub.length))
	}
}

/**
 * The masthead: the title in Anton capitals with its accent, the kicker in small capitals under
 * it, speed lines bursting from the corner. [art] is the mascot, standing at the start of the bar.
 */
@Composable
fun KitTopBar(
	title: String,
	modifier: Modifier = Modifier,
	titleAccent: String? = null,
	subtitle: String? = null,
	leading: (@Composable () -> Unit)? = null,
	actions: (@Composable RowScope.() -> Unit)? = null,
	art: Painter? = null,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val upper = personality.type.uppercaseHeadings
	val annotatedTitle = remember(title, titleAccent, colors.primary, upper) {
		accentTitle(if (upper) title.uppercase() else title, if (upper) titleAccent?.uppercase() else titleAccent, colors.primary)
	}
	Row(
		modifier = modifier
			.fillMaxWidth()
			.background(colors.background)
			.clipToBounds()
			.speedLineWash(color = colors.onSurface)
			.statusBarsPadding()
			.padding(start = if (art != null) 10.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(11.dp),
	) {
		leading?.invoke()
		if (art != null) {
			Image(painter = art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.height(52.dp))
		}
		Column(modifier = Modifier.weight(1f)) {
			Text(text = annotatedTitle, style = personality.type.title, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
			subtitle?.let {
				KitText(text = it, modifier = Modifier.padding(top = 2.dp), role = KitTextRole.Overline, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}
		actions?.let { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = it) }
	}
}

@Immutable
data class KitNavItem(val id: String, val label: String, val icon: ImageVector)

/** The tabs: the chosen one is a stamp, the accent with the pen around it. */
@Composable
fun KitBottomBar(items: List<KitNavItem>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val stampShape = remember(personality.shape.cornerSmall) { kitShape(personality.shape.cornerSmall) }
	Row(
		modifier = modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().height(66.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		items.forEach { item ->
			val selected = item.id == selectedId
			val interaction = remember { MutableInteractionSource() }
			val focused by interaction.collectIsFocusedAsState()
			Column(
				modifier = Modifier
					.weight(1f)
					.fillMaxHeight()
					.clickable(interactionSource = interaction, indication = null, onClick = { onSelect(item.id) })
					.inkFocusRing(focused = { focused }, color = colors.primary),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Box(
					modifier = Modifier
						.size(width = 48.dp, height = 30.dp)
						.background(color = if (selected) colors.primary else Color.Transparent, shape = stampShape)
						.border(width = 2.5.dp, color = if (selected) colors.outline else Color.Transparent, shape = stampShape),
					contentAlignment = Alignment.Center,
				) {
					Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp), tint = if (selected) colors.onPrimary else colors.onSurface)
				}
				Spacer(Modifier.height(4.dp))
				KitText(item.label, role = KitTextRole.Label, color = if (selected) colors.onSurface else colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
			}
		}
	}
}

/** The page: grid paper under everything, a 3 dp rule of ink closing each bar. */
@Composable
fun KitScaffold(
	modifier: Modifier = Modifier,
	topBar: (@Composable () -> Unit)? = null,
	bottomBar: (@Composable () -> Unit)? = null,
	grid: Boolean = true,
	dividers: Boolean = true,
	content: @Composable (PaddingValues) -> Unit,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	Scaffold(
		modifier = modifier.fillMaxSize(),
		containerColor = colors.background,
		contentColor = colors.onBackground,
		topBar = {
			topBar?.let { bar ->
				Column {
					bar()
					if (dividers) HorizontalDivider(thickness = 3.dp, color = colors.outline)
				}
			}
		},
		bottomBar = {
			bottomBar?.let { bar ->
				Column {
					if (dividers) HorizontalDivider(thickness = 3.dp, color = colors.outline)
					bar()
				}
			}
		},
	) { innerPadding ->
		Box(Modifier.fillMaxSize()) {
			if (grid) Box(Modifier.fillMaxSize().motifBackground(personality.motif, colors.onSurface, colors.gridOpacity))
			content(innerPadding)
		}
	}
}
