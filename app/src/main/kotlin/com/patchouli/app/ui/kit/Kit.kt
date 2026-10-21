/*
 * Components of the kit: KitText, KitHeadline, KitSurface, KitButton, KitIconButton, KitChip,
 * KitBadge, KitCheckbox, KitTextField, KitHorizontalDivider, KitTopBar, KitBottomBar, KitScaffold.
 * Their structure and API come from Komi Store (kurikomi-labs/komi-store), Apache License 2.0; the
 * look (rounded shapes, soft shadows, the check motif) is our own and comes from the app's style.
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.patchouli.app.R

// ---------------------------------------------------------------- text

enum class KitTextRole { Display, Title, Stamp, Label, Body, Mono }

private val KitTextRole.isHeading: Boolean
	get() = this == KitTextRole.Display || this == KitTextRole.Title || this == KitTextRole.Stamp || this == KitTextRole.Label

private fun kitTextStyle(
	type: PersonalityType, role: KitTextRole,
	fontSize: TextUnit, fontWeight: FontWeight?, letterSpacing: TextUnit, lineHeight: TextUnit,
): TextStyle {
	var style = when (role) {
		KitTextRole.Display -> type.display
		KitTextRole.Title -> type.title
		KitTextRole.Stamp -> type.stamp
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
	val resolvedUppercase = uppercase ?: (role.isHeading && personality.type.uppercaseHeadings)
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

/** The headline marker: a 2 × 2 check in the two accents. */
@Composable
fun HeadlineMark(size: Dp = 14.dp) {
	val colors = LocalPersonality.current.colors
	Box(Modifier.size(size).clip(RoundedCornerShape(3.dp)).checkerMark(colors.primary, colors.secondary))
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

private fun elevationOf(elevation: KitSurfaceElevation, shadow: PersonalityShadow): Dp =
	when (elevation) {
		KitSurfaceElevation.Flat -> 0.dp
		KitSurfaceElevation.Card -> shadow.card
		KitSurfaceElevation.Raised -> shadow.raised
		KitSurfaceElevation.Modal -> shadow.modal
	}

/**
 * A panel. `tilt` and `topEdgeOnly` are kept from the old API and ignored or softened: the style has
 * no tilted stamps.
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
	@Suppress("UNUSED_PARAMETER") tilt: Float = 0f,
	topEdgeOnly: Boolean = false,
	borderColor: Color = Color.Unspecified,
	contentPadding: PaddingValues = PaddingValues(0.dp),
	content: @Composable () -> Unit,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val shape = if (topEdgeOnly) RoundedCornerShape(topStart = personality.shape.corner, topEnd = personality.shape.corner) else kitShape(personality.shape.corner)
	val fill = if (paper == KitSurfacePaper.Surface) colors.surface else colors.background
	val contentInk = if (paper == KitSurfacePaper.Surface) colors.onSurface else colors.onBackground
	val line = if (borderColor != Color.Unspecified) borderColor else colors.outline
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val focused by interaction.collectIsFocusedAsState()
	val press = animateFloatAsState(if (pressed && onClick != null) 1f else 0f, label = "kitSurfacePress")
	val toneModifier = when (screentone) {
		KitScreentone.None -> Modifier
		KitScreentone.Corner -> Modifier.screentoneCorner(colors.primary, colors.screentoneOpacity, boost = screentoneBoost)
		KitScreentone.Fill -> Modifier.screentoneFill(colors.primary, colors.screentoneOpacity)
	}
	Box(
		modifier = modifier
			.softPress { press.value }
			.then(if (onClick != null) Modifier.inkFocusRing(focused = { focused }, color = colors.primary, corner = personality.shape.corner) else Modifier)
			.softShadow(elevationOf(elevation, personality.shadow), shape, colors.shadow)
			.clip(shape)
			.background(fill)
			.then(toneModifier)
			.border(personality.shape.border, line, shape)
			.then(
				if (onClick != null || onLongClick != null) {
					Modifier.combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick ?: {})
				} else Modifier,
			)
			.padding(contentPadding),
	) {
		CompositionLocalProvider(LocalContentColor provides contentInk) { content() }
	}
}

// ---------------------------------------------------------------- buttons

enum class KitButtonVariant { Primary, Tonal, Outline, Text, Destructive }
enum class KitButtonSize { Sm, Md, Lg }

private data class ButtonMetrics(val height: Dp, val hPadding: Dp, val font: TextUnit, val icon: Dp, val gap: Dp)

private fun buttonMetrics(size: KitButtonSize): ButtonMetrics =
	when (size) {
		KitButtonSize.Sm -> ButtonMetrics(34.dp, 14.dp, 13.sp, 16.dp, 6.dp)
		KitButtonSize.Md -> ButtonMetrics(44.dp, 20.dp, 14.5.sp, 18.dp, 8.dp)
		KitButtonSize.Lg -> ButtonMetrics(54.dp, 26.dp, 16.5.sp, 22.dp, 10.dp)
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
		KitButtonVariant.Outline -> colors.primary.takeIf { contrastRatio(it, colors.background) >= 3f } ?: ambient
		KitButtonVariant.Text -> ambient
	}

@Composable
private fun buttonShape(height: Dp): androidx.compose.ui.graphics.Shape {
	val shape = LocalPersonality.current.shape
	return if (shape.pill) RoundedCornerShape(height / 2) else kitShape(shape.cornerSmall)
}

/** `emphasized` is kept from the old API; the style shows emphasis with a little more shadow. */
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
	val alpha = if (enabled) 1f else 0.45f
	val ambientInk = LocalContentColor.current
	val containerColor = if (container != Color.Unspecified) container else kitButtonContainer(variant, colors)
	val contentColor =
		if (container != Color.Unspecified) ensureContrast(colors.onPrimary, container, colors.onBackground, colors.background)
		else kitButtonContent(variant, colors, ambientInk)
	val filled = containerColor != Color.Transparent
	val shape = buttonShape(metrics.height)
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val focused by interaction.collectIsFocusedAsState()
	val press = animateFloatAsState(if (pressed && enabled) 1f else 0f, label = "kitButtonPress")
	val lift = if (filled && variant != KitButtonVariant.Tonal) personality.shadow.button * (if (emphasized) 3 else 1) else 0.dp
	Row(
		modifier = modifier
			.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
			.softPress { press.value }
			.inkFocusRing(focused = { focused }, color = colors.primary, corner = metrics.height / 2)
			.softShadow(if (enabled) lift else 0.dp, shape, colors.shadow)
			.clip(shape)
			.background(containerColor.copy(alpha = containerColor.alpha * alpha))
			.then(if (variant == KitButtonVariant.Outline) Modifier.border(1.5.dp, contentColor.copy(alpha = 0.55f * alpha), shape) else Modifier)
			.clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
			.height(metrics.height)
			.padding(horizontal = if (variant == KitButtonVariant.Text) metrics.hPadding / 2 else metrics.hPadding),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment = Alignment.CenterVertically,
	) {
		CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = alpha)) {
			if (leadingIcon != null) {
				Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon))
				Spacer(Modifier.width(metrics.gap))
			}
			KitText(label, role = KitTextRole.Label, color = contentColor.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = metrics.font, uppercase = false)
			if (trailingIcon != null) {
				Spacer(Modifier.width(metrics.gap))
				Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon))
			}
		}
	}
}

@Composable
fun KitIconButton(
	icon: ImageVector,
	contentDescription: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	variant: KitButtonVariant = KitButtonVariant.Outline,
	enabled: Boolean = true,
	size: Dp = 40.dp,
) {
	val colors = LocalPersonality.current.colors
	val alpha = if (enabled) 1f else 0.4f
	val container = if (variant == KitButtonVariant.Outline) colors.surfaceVariant else kitButtonContainer(variant, colors)
	val content = if (variant == KitButtonVariant.Outline) colors.onSurface else kitButtonContent(variant, colors, LocalContentColor.current)
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val press = animateFloatAsState(if (pressed && enabled) 1f else 0f, label = "kitIconButtonPress")
	Box(
		modifier = modifier
			.softPress { press.value }
			.size(size)
			.clip(CircleShape)
			.background(container.copy(alpha = container.alpha * alpha))
			.clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = content.copy(alpha = alpha))
	}
}

// ---------------------------------------------------------------- chips and badges

enum class KitChipKind { Info, Filter }

private data class ChipMetrics(val height: Dp, val paddingX: Dp, val font: Float, val icon: Dp)

private fun chipMetrics(kind: KitChipKind, small: Boolean): ChipMetrics =
	when {
		kind == KitChipKind.Filter && small -> ChipMetrics(28.dp, 12.dp, 12.5f, 14.dp)
		kind == KitChipKind.Filter -> ChipMetrics(36.dp, 15.dp, 14f, 16.dp)
		small -> ChipMetrics(24.dp, 9.dp, 11.5f, 13.dp)
		else -> ChipMetrics(28.dp, 11.dp, 12f, 14.dp)
	}

/** A chip. `fill`/`ink` allow a semantic colour; `index` and `tilt` are kept from the old API. */
@Composable
fun KitChip(
	label: String,
	modifier: Modifier = Modifier,
	kind: KitChipKind = KitChipKind.Info,
	small: Boolean = false,
	selected: Boolean = false,
	@Suppress("UNUSED_PARAMETER") index: Int = 0,
	@Suppress("UNUSED_PARAMETER") tilt: Boolean = true,
	leadingIcon: ImageVector? = null,
	fill: Color = Color.Unspecified,
	ink: Color = Color.Unspecified,
	onClick: (() -> Unit)? = null,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val metrics = chipMetrics(kind, small)
	val isFilter = kind == KitChipKind.Filter
	val bg = when {
		fill != Color.Unspecified -> fill
		selected -> colors.primary
		isFilter -> colors.surface
		else -> colors.surfaceVariant
	}
	val fg = when {
		ink != Color.Unspecified -> ink
		fill != Color.Unspecified -> inkOn(fill, colors.onBackground, colors.background)
		selected -> colors.onPrimary
		else -> colors.onSurface
	}
	val shape = RoundedCornerShape(if (personality.shape.pill) metrics.height / 2 else personality.shape.cornerSmall)
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val press = animateFloatAsState(if (pressed) 1f else 0f, label = "kitChipPress")
	Row(
		modifier = modifier
			.softPress { press.value }
			.clip(shape)
			.background(bg)
			.then(if (isFilter && !selected && fill == Color.Unspecified) Modifier.border(1.dp, colors.outline, shape) else Modifier)
			.then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
			.height(metrics.height)
			.padding(horizontal = metrics.paddingX),
		horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon), tint = fg)
		Text(
			text = label,
			style = personality.type.label.copy(fontSize = metrics.font.sp, fontWeight = if (isFilter) FontWeight.Bold else FontWeight.SemiBold),
			color = fg, maxLines = 1, overflow = TextOverflow.Clip,
		)
	}
}

enum class KitBadgeTone { Alert, Neutral }

@Composable
fun KitBadge(
	text: String,
	modifier: Modifier = Modifier,
	tone: KitBadgeTone = KitBadgeTone.Neutral,
	@Suppress("UNUSED_PARAMETER") tilt: Boolean = false,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val fill = if (tone == KitBadgeTone.Neutral) colors.primary else colors.error
	val fg = if (tone == KitBadgeTone.Neutral) colors.onPrimary else colors.onError
	Box(
		modifier = modifier
			.height(20.dp).widthIn(min = 20.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(fill)
			.padding(horizontal = 6.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(text = text, color = fg, style = personality.type.label.copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, fontFeatureSettings = "tnum"))
	}
}

// ---------------------------------------------------------------- inputs

@Composable
fun KitCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
	val colors = LocalPersonality.current.colors
	val alpha = if (enabled) 1f else 0.45f
	val fill by animateColorAsState(if (checked) colors.primary else colors.surface, label = "kitCheckboxFill")
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	val shape = RoundedCornerShape(6.dp)
	val toggleModifier =
		if (onCheckedChange != null) {
			Modifier.toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
		} else Modifier
	Box(
		modifier = modifier
			.then(toggleModifier)
			.size(22.dp)
			.inkFocusRing(focused = { focused }, color = colors.primary, corner = 6.dp)
			.clip(shape)
			.background(color = fill.copy(alpha = fill.alpha * alpha))
			.then(if (checked) Modifier else Modifier.border(width = 1.5.dp, color = colors.onSurfaceVariant.copy(alpha = 0.7f * alpha), shape = shape)),
		contentAlignment = Alignment.Center,
	) {
		if (checked) Icon(ImageVector.vectorResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.onPrimary.copy(alpha = alpha))
	}
}

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
	val shape = kitShape(personality.shape.cornerSmall)
	val border by animateColorAsState(if (focused) colors.primary else colors.outline, label = "kitFieldBorder")
	Column(modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(0.5f))) {
		if (label != null) {
			Text(text = label, style = type.label.copy(fontSize = 13.sp), color = colors.onSurfaceVariant)
			Spacer(Modifier.height(6.dp))
		}
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clip(shape)
				.background(if (enabled) colors.surface else colors.surfaceVariant)
				.border(width = if (focused) 2.dp else 1.dp, color = border, shape = shape)
				.then(if (enabled) Modifier.clickable(interactionSource = shellInteraction, indication = null, onClick = { focusRequester.requestFocus() }) else Modifier)
				.then(if (multiline) Modifier.padding(12.dp) else Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp)),
			verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(9.dp),
		) {
			BasicTextField(
				value = value,
				onValueChange = onValueChange,
				modifier = Modifier.weight(1f).focusRequester(focusRequester),
				enabled = enabled,
				textStyle = type.body.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, color = colors.onSurface),
				cursorBrush = SolidColor(colors.primary),
				singleLine = !multiline,
				minLines = if (multiline) rows else 1,
				maxLines = if (multiline) Int.MAX_VALUE else 1,
				keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (!multiline && onCommit != null) ImeAction.Done else ImeAction.Default),
				keyboardActions = KeyboardActions(onDone = { onCommit?.invoke() }),
				interactionSource = interaction,
				decorationBox = { inner ->
					if (value.isEmpty() && placeholder != null) {
						Text(text = placeholder, style = type.body.copy(fontSize = 15.sp), color = colors.onSurfaceVariant, maxLines = 1)
					}
					inner()
				},
			)
		}
		if (helper != null) {
			Spacer(Modifier.height(6.dp))
			Text(text = helper, style = type.body.copy(fontSize = 12.sp), color = colors.onSurfaceVariant)
		}
	}
}

@Composable
fun KitHorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = Color.Unspecified) {
	val colors = LocalPersonality.current.colors
	val resolved = if (color != Color.Unspecified) color else colors.outline
	Box(modifier.fillMaxWidth().height(minOf(thickness, 1.5.dp)).background(resolved))
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

/** [art] is a figure that stands at the end of the bar, behind the title: the mascot of the app. */
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
	val annotatedTitle = remember(title, titleAccent, colors.primary) { accentTitle(title, titleAccent, colors.primary) }
	Box(
		modifier = modifier
			.fillMaxWidth()
			.background(colors.background)
			.screentoneCorner(colors.primary, colors.screentoneOpacity, boost = 1.4f, regionWidth = 150.dp, regionHeight = 80.dp)
			.statusBarsPadding(),
	) {
		if (art != null) {
			Image(
				painter = art, contentDescription = null, contentScale = ContentScale.Fit,
				modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp).height(84.dp),
			)
		}
		Row(
			modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			leading?.invoke()
			Column(modifier = Modifier.weight(1f)) {
				Text(text = annotatedTitle, style = personality.type.display.copy(fontSize = 26.sp, lineHeight = 30.sp), color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
				subtitle?.let {
					KitText(text = it, modifier = Modifier.padding(top = 1.dp), role = KitTextRole.Label, color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
			}
			actions?.let { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = it) }
		}
	}
}

@Immutable
data class KitNavItem(val id: String, val label: String, val icon: ImageVector)

@Composable
fun KitBottomBar(items: List<KitNavItem>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
	val colors = LocalPersonality.current.colors
	Row(
		modifier = modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().height(68.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		items.forEach { item ->
			val selected = item.id == selectedId
			val interaction = remember { MutableInteractionSource() }
			val focused by interaction.collectIsFocusedAsState()
			val pill by animateColorAsState(if (selected) colors.primary.copy(alpha = 0.16f) else Color.Transparent, label = "kitNavPill")
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
					modifier = Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(pill),
					contentAlignment = Alignment.Center,
				) {
					Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(21.dp), tint = if (selected) colors.primary else colors.onSurfaceVariant)
				}
				Spacer(Modifier.height(4.dp))
				KitText(item.label, role = KitTextRole.Label, color = if (selected) colors.onSurface else colors.onSurfaceVariant, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
			}
		}
	}
}

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
					if (dividers) HorizontalDivider(thickness = 1.dp, color = colors.outline)
				}
			}
		},
		bottomBar = {
			bottomBar?.let { bar ->
				Column {
					if (dividers) HorizontalDivider(thickness = 1.dp, color = colors.outline)
					bar()
				}
			}
		},
	) { innerPadding ->
		Box(Modifier.fillMaxSize()) {
			if (grid) Box(Modifier.fillMaxSize().motifBackground(personality.motif, colors.primary, colors.gridOpacity))
			content(innerPadding)
		}
	}
}
