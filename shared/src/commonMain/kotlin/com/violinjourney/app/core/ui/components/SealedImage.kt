package com.violinjourney.app.core.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Seals a picture drawn once and laid down on many frames — a baked shadow: nothing draws into it any more. On iOS skiko makes an
 * image of a bitmap every time the node that draws it records, and only a sealed one shares its pixels instead of copying them
 * (docs/plan-performance.md, «На iOS»); Android keeps a bitmap's texture by itself, and nothing is done there.
 */
expect fun ImageBitmap.seal(): ImageBitmap
