package com.violinjourney.app.feature.settings

/**
 * The system camera of the platform shoots in «Качество видео» (spec 3.19): on iOS it is told, on Android its apps do not listen —
 * there the row says the choice is the own camera's under the backing.
 */
internal expect val systemCameraTakesVideoQuality: Boolean
