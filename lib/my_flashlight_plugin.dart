/// Flashlight (camera torch) control. Native support: Android only.
library;

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Why the flashlight could not be switched.
enum FlashlightError {
  /// iOS, web, desktop: the plugin has no native code there.
  unsupportedPlatform,

  /// The device has no camera flash (many tablets, emulators).
  noFlash,

  /// The FLASHLIGHT permission is missing from AndroidManifest.xml.
  permissionDenied,

  /// The camera is busy (used by another app) or failed.
  cameraError,
}

/// Thrown by [MyFlashlightPlugin] instead of a crash.
class FlashlightException implements Exception {
  const FlashlightException(this.error, [this.details]);

  final FlashlightError error;

  /// Technical details from the native side, for logs.
  final String? details;

  @override
  String toString() => 'FlashlightException(${error.name}, $details)';
}

/// Public API of the plugin. All platform-channel code lives here: the app
/// only calls these static methods.
abstract final class MyFlashlightPlugin {
  /// Channel to the Kotlin `MyFlashlightPlugin` class.
  @visibleForTesting
  static const channel = MethodChannel('my_flashlight_plugin');

  /// Whether this platform has a native implementation (Android only).
  static bool get isSupportedPlatform =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// Switches the flashlight and returns the new state (`true` = on).
  ///
  /// Throws [FlashlightException] on an unsupported platform or device.
  static Future<bool> toggle() => _invoke('toggle');

  /// Switches the flashlight off (does nothing if it is already off).
  static Future<void> turnOff() => _invoke('turnOff');

  /// Whether the flashlight can be used here. Never throws.
  static Future<bool> isAvailable() async {
    try {
      return await _invoke('isAvailable');
    } on FlashlightException {
      return false;
    }
  }

  static Future<bool> _invoke(String method) async {
    // Graceful degradation: no channel call where there is no native code.
    if (!isSupportedPlatform) {
      throw const FlashlightException(FlashlightError.unsupportedPlatform);
    }
    try {
      return await channel.invokeMethod<bool>(method) ?? false;
    } on PlatformException catch (e) {
      throw FlashlightException(_errorOf(e.code), e.message);
    } on MissingPluginException catch (e) {
      throw FlashlightException(FlashlightError.unsupportedPlatform, e.message);
    }
  }

  static FlashlightError _errorOf(String code) => switch (code) {
    'NO_FLASH' => FlashlightError.noFlash,
    'PERMISSION_DENIED' => FlashlightError.permissionDenied,
    _ => FlashlightError.cameraError,
  };
}
