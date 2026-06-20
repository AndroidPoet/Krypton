package io.krypton.sample.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIDevice

actual fun platformName(): String =
    "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"

/**
 * iOS entry point — consumed by the Xcode `iosApp` project via
 * `ComposeApp.MainViewControllerKt.MainViewController()`.
 */
@Suppress("FunctionName", "unused")
fun MainViewController() = ComposeUIViewController { App() }
