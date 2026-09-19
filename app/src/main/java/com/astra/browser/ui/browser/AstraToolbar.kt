package com.astra.browser.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.astra.browser.theme.LocalAstraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstraToolbar(
    addressText: String,
    onAddressChange: (String) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    isSecure: Boolean,
    isLoading: Boolean,
    isBookmarked: Boolean,
    isPrivate: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTabSwitcherClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val colors = LocalAstraColors.current

    Surface(color = if (isPrivate) colors.surfaceVariant else colors.toolbar, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = if (canGoBack) colors.onSurface else colors.onSurface.copy(alpha = 0.3f))
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward",
                    tint = if (canGoForward) colors.onSurface else colors.onSurface.copy(alpha = 0.3f))
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSecure) Icons.Filled.Lock else Icons.Filled.Info,
                        contentDescription = if (isSecure) "Secure connection" else "Not secure",
                        tint = if (isSecure) colors.accent else colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    AstraAddressField(
                        value = addressText,
                        onValueChange = onAddressChange,
                        onFocusChange = onAddressFocusChange,
                        onSubmit = { onNavigate(addressText) },
                        modifier = Modifier.weight(1f)
                    )
                    if (addressText.isNotBlank()) {
                        IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) colors.accent else colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onReloadOrStop) {
                Icon(
                    imageVector = if (isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = if (isLoading) "Stop" else "Reload",
                    tint = colors.onSurface
                )
            }

            TabCounterButton(count = tabCount, onClick = onTabSwitcherClick)

            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = colors.onSurface)
            }
        }
    }
}

/**
 * Address bar field built to behave like a real browser's, fixing the three
 * things that made the old bare BasicTextField feel "awkward":
 *
 * 1. Tapping into a URL used to drop the cursor wherever you tapped, same as
 *    typing in any ordinary text box -- so replacing a long pasted URL meant
 *    manually select-all-ing (or holding backspace) every time. Real
 *    browsers select the ENTIRE url the moment you focus the field, so
 *    typing or pasting immediately just replaces it. Now: on the focus
 *    transition from unfocused -> focused, the whole text is selected.
 * 2. There was no clear/X button at all -- the only way to empty the field
 *    was manual deletion. Now a small X appears whenever the field has
 *    focus AND text, one tap clears it (and keeps focus + keyboard up, so
 *    you can immediately type/paste a new address).
 * 3. Selection state (TextFieldValue, not a bare String) is now owned
 *    locally so we can actually programmatically select-all / clear without
 *    fighting the caller's plain-String addressText state -- the caller
 *    still only ever sees plain Strings via onValueChange, keeping the
 *    rest of the toolbar/viewmodel wiring unchanged.
 */
@Composable
private fun AstraAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAstraColors.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Deliberately remember(Unit) -- NOT remember(value). Keying this on
    // `value` would re-run `mutableStateOf(...)` and silently replace
    // fieldValue (selection and all) on every single keystroke, since typing
    // also changes `value` via the round-trip through the caller's state.
    // That was fighting the cursor/selection on every character typed.
    // Sync with external changes to `value` is handled entirely by the
    // LaunchedEffect below instead.
    var fieldValue by remember(Unit) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value))
    }
    // Keep local state in sync when addressText changes from OUTSIDE this
    // field (navigating, switching tabs) rather than from the user typing
    // in it -- otherwise every keystroke would get clobbered by the
    // `remember(value)` re-init above racing the caller's own state update.
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = androidx.compose.ui.text.input.TextFieldValue(value)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                fieldValue = new
                onValueChange(new.text)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusEvent { focusState ->
                    val wasFocused = isFocused
                    isFocused = focusState.isFocused
                    onFocusChange(focusState.isFocused)
                    // The actual fix: select everything the instant the
                    // field GAINS focus (not on every recomposition -- only
                    // on the unfocused -> focused transition), so the very
                    // next keystroke or paste replaces the whole URL
                    // instead of inserting into the middle of it.
                    if (focusState.isFocused && !wasFocused) {
                        fieldValue = fieldValue.copy(
                            selection = androidx.compose.ui.text.TextRange(0, fieldValue.text.length)
                        )
                    }
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go,
                // Address bars keep autocorrect off -- it actively fights
                // typing URLs (capitalizing, "correcting" domain names).
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
            decorationBox = { inner ->
                if (fieldValue.text.isEmpty()) {
                    Text(
                        "Search or enter address",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface.copy(alpha = 0.4f)
                    )
                }
                inner()
            }
        )

        // Clear (X) button: only while focused with something to clear --
        // matching Chrome/Brave, it doesn't clutter the bar while you're
        // just browsing with the field unfocused.
        if (isFocused && fieldValue.text.isNotEmpty()) {
            IconButton(
                onClick = {
                    fieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                    onValueChange("")
                    // Keep focus + keyboard open after clearing, so the user
                    // can immediately type or paste a replacement without an
                    // extra tap.
                    focusRequester.requestFocus()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TabCounterButton(count: Int, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.onSurface),
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurface
                    )
                }
            }
        }
    }
}


