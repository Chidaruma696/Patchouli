/*
 * More of the kit: KitSegmented, KitList (container + row), KitSheet (bottom placement), KitDialog,
 * KitProgress, KitFab and the settings section head.
 */
package com.patchouli.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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

@Composable
fun <T> KitSegmented(
	selected: T,
	items: List<KitSegmentedItem<T>>,
	onSelect: (T) -> Unit,
	modifier: Modifier = Modifier,
	height: Dp = 40.dp,
	fillWidth: Boolean = false,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val outer = RoundedCornerShape(if (personality.shape.pill) height / 2 else personality.shape.cornerSmall)
	Row(
		modifier = modifier
			.height(height)
			.clip(outer)
			.background(colors.surfaceVariant)
			.padding(3.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		items.forEach { item ->
			val active = selected == item.value
			val interaction = remember { MutableInteractionSource() }
			val focused by interaction.collectIsFocusedAsState()
			val inner = RoundedCornerShape(if (personality.shape.pill) (height - 6.dp) / 2 else personality.shape.cornerSmall - 2.dp)
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.then(if (fillWidth) Modifier.weight(1f) else Modifier.defaultMinSize(minWidth = height))
					.inkFocusRing(focused = { focused }, color = colors.primary)
					.then(if (active) Modifier.softShadow(personality.shadow.card, inner, colors.shadow) else Modifier)
					.clip(inner)
					.background(if (active) colors.surface else Color.Transparent)
					.clickable(interactionSource = interaction, indication = null) { onSelect(item.value) }
					.padding(horizontal = Spacing.md),
				contentAlignment = Alignment.Center,
			) {
				val ink = if (active) colors.primary else colors.onSurfaceVariant
				Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
					item.icon?.let { Icon(it, contentDescription = item.title, modifier = Modifier.size(18.dp), tint = ink) }
					item.title?.let { KitText(it, role = KitTextRole.Label, color = ink, fontSize = 13.sp, maxLines = 1, uppercase = false) }
				}
			}
		}
	}
}

// ---------------------------------------------------------------- lists

@Composable
fun KitListContainer(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	val shape = kitShape(personality.shape.corner)
	Column(
		modifier = modifier
			.fillMaxWidth()
			.softShadow(personality.shadow.card, shape, colors.shadow)
			.clip(shape)
			.background(colors.surface)
			.border(width = personality.shape.border, color = colors.outline, shape = shape),
		content = content,
	)
}

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
		Modifier.combinedClickable(
			interactionSource = interaction,
			indication = null,
			enabled = enabled,
			onLongClick = onLongClick,
			onClick = onClick ?: {},
		)
	}
	Row(
		modifier = modifier
			.fillMaxWidth()
			.inkFocusRing(focused = { focused }, color = colors.primary)
			.then(if (selected) Modifier.background(colors.primary.copy(alpha = 0.08f)) else Modifier)
			.then(
				if (showDivider) Modifier.drawBehind {
					val stroke = 1.dp.toPx()
					val y = size.height - stroke / 2f
					drawLine(color = colors.outline, start = Offset(16.dp.toPx(), y), end = Offset(size.width - 16.dp.toPx(), y), strokeWidth = stroke)
				} else Modifier,
			)
			.then(clickModifier)
			.padding(horizontal = 16.dp, vertical = 12.dp)
			.alpha(if (enabled) 1f else 0.45f),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		if (icon != null) {
			Box(
				modifier = Modifier
					.size(40.dp)
					.clip(RoundedCornerShape(if (personality.shape.pill) 20.dp else personality.shape.cornerSmall))
					.background(if (destructive) colors.error.copy(alpha = 0.12f) else colors.primary.copy(alpha = 0.12f)),
				contentAlignment = Alignment.Center,
			) {
				Icon(icon, contentDescription = null, tint = if (destructive) colors.error else colors.primary, modifier = Modifier.size(21.dp))
			}
		}
		Column(modifier = Modifier.weight(1f)) {
			KitText(
				text = title,
				role = KitTextRole.Label,
				color = titleColor,
				fontSize = 15.sp,
				fontWeight = if (strong) FontWeight.Black else null,
				uppercase = false,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			subtitle?.let {
				KitText(text = it, role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, maxLines = 2, overflow = TextOverflow.Ellipsis)
			}
		}
		trailing?.invoke()
	}
}

/** Section head with the check mark, the Japanese kicker and a hairline to the edge. */
@Composable
fun KitSectionHead(
	label: String,
	modifier: Modifier = Modifier,
	kicker: String? = null,
	action: (@Composable () -> Unit)? = null,
) {
	val colors = LocalPersonality.current.colors
	Row(
		modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		HeadlineMark(12.dp)
		KitText(text = label, role = KitTextRole.Stamp, color = colors.onSurface, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
		if (kicker != null) {
			KitText(text = kicker, role = KitTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false, maxLines = 1)
		}
		Box(Modifier.weight(1f).height(1.dp).background(colors.outline))
		action?.invoke()
	}
}

// ---------------------------------------------------------------- overlays

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
		shape = RoundedCornerShape(topStart = personality.shape.corner + 6.dp, topEnd = personality.shape.corner + 6.dp),
		containerColor = colors.background,
		contentColor = colors.onSurface,
		scrimColor = colors.shadow.copy(alpha = 0.45f),
		dragHandle = null,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.then(if (screentone) Modifier.screentoneCorner(colors.primary, colors.screentoneOpacity, boost = 1.5f, regionWidth = 150.dp, regionHeight = 110.dp) else Modifier)
				.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp),
		) {
			Box(Modifier.fillMaxWidth().padding(bottom = if (title != null) 12.dp else 8.dp), contentAlignment = Alignment.Center) {
				Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.onSurfaceVariant.copy(alpha = 0.4f)))
			}
			if (title != null) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					HeadlineMark(12.dp)
					KitText(text = title, role = KitTextRole.Title, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
					if (titleJp != null) {
						KitText(text = titleJp, role = KitTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false, maxLines = 1)
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

// ---------------------------------------------------------------- progress

@Composable
fun KitCircularProgress(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
	val colors = LocalPersonality.current.colors
	CircularProgressIndicator(
		modifier = modifier,
		color = if (color != Color.Unspecified) color else colors.primary,
		strokeWidth = 3.5.dp,
		strokeCap = StrokeCap.Round,
		trackColor = colors.primary.copy(alpha = 0.14f),
	)
}

@Composable
fun KitLinearProgress(modifier: Modifier = Modifier, progress: (() -> Float)? = null) {
	val colors = LocalPersonality.current.colors
	if (progress == null) {
		LinearProgressIndicator(modifier = modifier.clip(RoundedCornerShape(2.dp)), color = colors.primary, trackColor = colors.primary.copy(alpha = 0.14f), strokeCap = StrokeCap.Round)
	} else {
		LinearProgressIndicator(progress = progress, modifier = modifier.clip(RoundedCornerShape(2.dp)), color = colors.primary, trackColor = colors.primary.copy(alpha = 0.14f), strokeCap = StrokeCap.Round)
	}
}

// ---------------------------------------------------------------- fab

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
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val focused by interaction.collectIsFocusedAsState()
	val press = animateFloatAsState(if (pressed) 1f else 0f, label = "kitFabPress")
	val shape = RoundedCornerShape(if (personality.shape.pill) 28.dp else personality.shape.corner)
	val base = modifier
		.softPress { press.value }
		.softShadow(personality.shadow.raised, shape, colors.shadow)
		.clip(shape)
		.background(colors.primary)
		.inkFocusRing(focused = { focused }, color = colors.primary)
		.clickable(interactionSource = interaction, indication = null, onClick = onClick)
	if (label != null) {
		Row(
			modifier = base.height(56.dp).padding(horizontal = 20.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Icon(icon, contentDescription, modifier = Modifier.size(22.dp), tint = colors.onPrimary)
			KitText(label, role = KitTextRole.Label, color = colors.onPrimary, fontSize = 15.sp, uppercase = false)
		}
	} else {
		Box(modifier = base.size(56.dp), contentAlignment = Alignment.Center) {
			Icon(icon, contentDescription, modifier = Modifier.size(26.dp), tint = colors.onPrimary)
		}
	}
}

/** Bounded-height helper for sheets that host their own lazy lists. */
fun Modifier.sheetMaxHeight(max: Dp): Modifier = heightIn(max = max)