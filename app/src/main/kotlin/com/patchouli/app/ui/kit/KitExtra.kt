/*
 * More of the kit: KitSegmented, KitList (container + row), KitSheet (bottom placement), KitDialog,
 * KitProgress, KitFab and the section head. Same manga look as Kit.kt, after Komi Store.
 */
package com.patchouli.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ---------------------------------------------------------------- segmented

@Immutable
data class KitSegmentedItem<T>(val value: T, val title: String? = null, val icon: ImageVector? = null)

/** A strip of paper with the pen around it and a block behind, cut into cells by rules of ink; the chosen cell is the accent. */
@Composable
fun <T> KitSegmented(
	selected: T,
	items: List<KitSegmentedItem<T>>,
	onSelect: (T) -> Unit,
	modifier: Modifier = Modifier,
	height: Dp = 42.dp,
	fillWidth: Boolean = false,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val shape = remember(personality.shape.cornerSmall) { kitShape(personality.shape.cornerSmall) }
	val line = personality.shape.borderButton
	Row(
		modifier = modifier
			.height(height)
			.hardShadow(offset = DpOffset(3.dp, 3.dp), color = colors.shadow, shape = shape)
			.background(colors.surface, shape)
			.border(line, colors.outline, shape)
			.clip(shape),
		verticalAlignment = Alignment.CenterVertically,
	) {
		items.forEachIndexed { index, item ->
			if (index > 0) Box(Modifier.width(line).fillMaxHeight().background(colors.outline))
			val active = selected == item.value
			val interaction = remember { MutableInteractionSource() }
			val focused by interaction.collectIsFocusedAsState()
			val ink = if (active) colors.onPrimary else colors.onSurface
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.then(if (fillWidth) Modifier.weight(1f) else Modifier.defaultMinSize(minWidth = height))
					.inkFocusRing(focused = { focused }, color = colors.primary)
					.background(if (active) colors.primary else Color.Transparent)
					.clickable(interactionSource = interaction, indication = null) { onSelect(item.value) }
					.padding(horizontal = Spacing.md),
				contentAlignment = Alignment.Center,
			) {
				Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
					item.icon?.let { Icon(it, contentDescription = item.title, modifier = Modifier.size(18.dp), tint = ink) }
					item.title?.let { KitText(it, role = KitTextRole.Stamp, color = ink, maxLines = 1) }
				}
			}
		}
	}
}

// ---------------------------------------------------------------- lists

/** A list is one panel: the pen's line around it and a 4 dp block behind. */
@Composable
fun KitListContainer(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	Column(
		modifier = modifier
			.fillMaxWidth()
			.hardShadow(offset = DpOffset(4.dp, 4.dp), color = colors.shadow, shape = RectangleShape)
			.background(color = colors.surface)
			.border(width = personality.shape.border, color = colors.outline),
		content = content,
	)
}

/** One row of the panel: a 2 dp rule under it, a bar of the accent at its start when selected, the icon in its own little box. */
@Composable
fun KitListRow(
	title: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	icon: ImageVector? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	strong: Boolean = false,
	destructive: Boolean = false,
	selected: Boolean = false,
	enabled: Boolean = true,
	showDivider: Boolean = false,
	trailing: @Composable (() -> Unit)? = null,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	val titleColor = if (destructive) colors.error else colors.onSurface
	val clickModifier = if (onClick == null && onLongClick == null) {
		Modifier
	} else {
		Modifier.combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick ?: {})
	}
	Row(
		modifier = modifier
			.fillMaxWidth()
			.inkFocusRing(focused = { focused }, color = colors.primary)
			.then(if (selected) Modifier.background(colors.surfaceVariant) else Modifier)
			.then(
				if (showDivider) Modifier.drawBehind {
					val stroke = 2.dp.toPx()
					val y = size.height - stroke / 2f
					drawLine(color = colors.outline, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = stroke)
				} else Modifier,
			)
			.then(if (selected) Modifier.drawBehind { drawRect(color = colors.primary, size = Size(5.dp.toPx(), size.height)) } else Modifier)
			.then(clickModifier)
			.padding(horizontal = 14.dp, vertical = 12.dp)
			.alpha(if (enabled) 1f else 0.45f),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		if (icon != null) {
			Box(
				modifier = Modifier.size(42.dp).background(color = colors.background).border(width = personality.shape.borderChip, color = colors.outline),
				contentAlignment = Alignment.Center,
			) {
				Icon(icon, contentDescription = null, tint = if (destructive) colors.error else colors.onSurface, modifier = Modifier.size(22.dp))
			}
		}
		Column(modifier = Modifier.weight(1f)) {
			KitText(
				text = title,
				role = KitTextRole.Label,
				color = titleColor,
				fontSize = 16.sp,
				lineHeight = 24.sp,
				fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			subtitle?.let {
				KitText(text = it, role = KitTextRole.Body, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
			}
		}
		trailing?.invoke()
	}
}

/** Section head as lettering: the stamp, the title in Anton capitals, the Japanese kicker, and a rule of ink to the edge. */
@Composable
fun KitSectionHead(
	label: String,
	modifier: Modifier = Modifier,
	kicker: String? = null,
	action: (@Composable () -> Unit)? = null,
) {
	val colors = LocalPersonality.current.colors
	Row(
		modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		HeadlineMark(12.dp)
		KitText(text = label, role = KitTextRole.Title, color = colors.onSurface, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.02.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
		if (kicker != null) {
			KitText(text = kicker, role = KitTextRole.Overline, color = colors.onSurfaceVariant, uppercase = false, maxLines = 1)
		}
		Box(Modifier.weight(1f).height(2.dp).background(colors.outline))
		action?.invoke()
	}
}

// ---------------------------------------------------------------- overlays

/** A sheet rising from the bottom edge: square, a 4 dp rule of ink along its top, the grabber in ink, the tone in the corner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitSheet(
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	title: String? = null,
	titleJp: String? = null,
	screentone: Boolean = true,
	footer: (@Composable () -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		modifier = modifier,
		sheetState = sheetState,
		shape = RectangleShape,
		containerColor = colors.background,
		contentColor = colors.onSurface,
		scrimColor = colors.shadow.copy(alpha = 0.55f),
		dragHandle = null,
	) {
		Column(Modifier.fillMaxWidth()) {
			Box(Modifier.fillMaxWidth().height(4.dp).background(colors.outline))
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.then(if (screentone) Modifier.screentoneCorner(colors.onSurface, colors.screentoneOpacity, boost = 1.6f, regionWidth = 150.dp, regionHeight = 110.dp) else Modifier)
					.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 22.dp),
			) {
				Box(Modifier.fillMaxWidth().padding(bottom = if (title != null) 12.dp else 8.dp), contentAlignment = Alignment.Center) {
					Box(Modifier.size(width = 46.dp, height = 5.dp).background(colors.outline))
				}
				if (title != null) {
					Row(
						modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						HeadlineMark(10.dp)
						KitText(text = title, role = KitTextRole.Label, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
						if (titleJp != null) {
							KitText(text = titleJp, role = KitTextRole.Body, color = colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
						}
					}
				}
				Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
				if (footer != null) {
					Box(Modifier.fillMaxWidth().padding(top = 16.dp)) { footer() }
				}
			}
		}
	}
}

/** A dialog is a modal panel: the page's paper, a 4 dp line and a 14 dp block behind. */
@Composable
fun KitDialog(
	onDismissRequest: () -> Unit,
	confirmButton: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	dismissButton: (@Composable () -> Unit)? = null,
	title: (@Composable () -> Unit)? = null,
	text: (@Composable () -> Unit)? = null,
	properties: DialogProperties = DialogProperties(),
) {
	Dialog(onDismissRequest = onDismissRequest, properties = properties) {
		KitSurface(
			modifier = modifier.fillMaxWidth(),
			elevation = KitSurfaceElevation.Modal,
			paper = KitSurfacePaper.Background,
			contentPadding = PaddingValues(22.dp),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
				title?.invoke()
				text?.invoke()
				Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
					dismissButton?.invoke()
					confirmButton()
				}
			}
		}
	}
}

// ---------------------------------------------------------------- slider

/** A slider drawn with the pen: a square track with a rule of ink around it, the accent filling it, and a stamp for a thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitSlider(
	value: Float,
	onValueChange: (Float) -> Unit,
	modifier: Modifier = Modifier,
	onValueChangeFinished: (() -> Unit)? = null,
	enabled: Boolean = true,
) {
	val colors = LocalPersonality.current.colors
	Slider(
		value = value,
		onValueChange = onValueChange,
		modifier = modifier,
		enabled = enabled,
		onValueChangeFinished = onValueChangeFinished,
		thumb = {
			Box(
				Modifier
					.size(width = 14.dp, height = 26.dp)
					.hardShadow(DpOffset(2.dp, 2.dp), colors.shadow)
					.background(colors.primary)
					.border(2.dp, colors.outline),
			)
		},
		track = { state ->
			val range = state.valueRange
			val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
			Box(Modifier.fillMaxWidth().height(12.dp).background(colors.surfaceVariant).border(2.dp, colors.outline)) {
				Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().padding(2.dp).background(colors.primary))
			}
		},
	)
}

// ---------------------------------------------------------------- progress

/** The ink spinner: a 4 dp stroke cut square. */
@Composable
fun KitCircularProgress(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
	val colors = LocalPersonality.current.colors
	CircularProgressIndicator(
		modifier = modifier,
		color = if (color != Color.Unspecified) color else colors.primary,
		strokeWidth = 4.dp,
		strokeCap = StrokeCap.Butt,
	)
}

/** A bar cut square: the accent over the well of the paper. */
@Composable
fun KitLinearProgress(modifier: Modifier = Modifier, progress: (() -> Float)? = null) {
	val colors = LocalPersonality.current.colors
	if (progress == null) {
		LinearProgressIndicator(modifier = modifier.clip(RectangleShape), color = colors.primary, trackColor = colors.surfaceVariant, strokeCap = StrokeCap.Butt, gapSize = 0.dp)
	} else {
		LinearProgressIndicator(progress = progress, modifier = modifier.clip(RectangleShape), color = colors.primary, trackColor = colors.surfaceVariant, strokeCap = StrokeCap.Butt, gapSize = 0.dp, drawStopIndicator = {})
	}
}

// ---------------------------------------------------------------- fab

/** The big stamp of the page: the accent, a 3 dp line and a 5 dp block that vanishes while pressed. */
@Composable
fun KitFab(
	onClick: () -> Unit,
	icon: ImageVector,
	contentDescription: String,
	modifier: Modifier = Modifier,
	label: String? = null,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val shape = remember(personality.shape.cornerSmall) { kitShape(personality.shape.cornerSmall) }
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val focused by interaction.collectIsFocusedAsState()
	val press = animateFloatAsState(if (pressed) 1f else 0f, label = "kitFabPress")
	val base = modifier
		.hardShadow(color = colors.shadow, shape = shape) { Offset(5.dp.toPx() * (1f - press.value), 5.dp.toPx() * (1f - press.value)) }
		.clip(shape)
		.background(colors.primary, shape)
		.border(3.dp, colors.outline, shape)
		.inkFocusRing(focused = { focused }, color = colors.primary)
		.clickable(interactionSource = interaction, indication = null, onClick = onClick)
	if (label != null) {
		Row(
			modifier = base.height(56.dp).padding(horizontal = 20.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Icon(icon, contentDescription, modifier = Modifier.size(22.dp), tint = colors.onPrimary)
			KitText(label, role = KitTextRole.Label, color = colors.onPrimary, fontSize = 16.sp)
		}
	} else {
		Box(modifier = base.size(56.dp), contentAlignment = Alignment.Center) {
			Icon(icon, contentDescription, modifier = Modifier.size(26.dp), tint = colors.onPrimary)
		}
	}
}

/** Bounded-height helper for sheets that host their own lazy lists. */
fun Modifier.sheetMaxHeight(max: Dp): Modifier = heightIn(max = max)
