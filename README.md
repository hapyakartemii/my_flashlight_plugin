# my_flashlight_plugin

Flutter plugin that switches the device flashlight (camera torch).
Native implementation: **Android only** (Kotlin, `CameraManager`).
On iOS, web and desktop it degrades gracefully: it throws a
`FlashlightException(FlashlightError.unsupportedPlatform)` that the app can
show as a dialog, and never crashes.

## Install (from Git)

```yaml
dependencies:
  my_flashlight_plugin:
    git:
      url: https://github.com/hapyakartemii/my_flashlight_plugin.git
```

## Usage

```dart
import 'package:my_flashlight_plugin/my_flashlight_plugin.dart';

try {
  final isOn = await MyFlashlightPlugin.toggle();
} on FlashlightException catch (e) {
  // e.error: unsupportedPlatform, noFlash, permissionDenied, cameraError
}
```

| Method | Result |
|---|---|
| `MyFlashlightPlugin.toggle()` | new state, `true` = on |
| `MyFlashlightPlugin.turnOff()` | switches off |
| `MyFlashlightPlugin.isAvailable()` | `false` on unsupported platforms / devices, never throws |
| `MyFlashlightPlugin.isSupportedPlatform` | `true` only on Android |

## How it works

* **Platform channel:** `MethodChannel('my_flashlight_plugin')`, hidden inside
  the plugin; the app only imports the Dart API.
* **Hardware safety:** before any camera call the Kotlin code checks
  `PackageManager.FEATURE_CAMERA_FLASH`, the `FLASHLIGHT` permission (declared
  in the plugin's `AndroidManifest.xml`, merged into the app) and that a camera
  with `FLASH_INFO_AVAILABLE` exists. Devices without a flash get the
  `NO_FLASH` error code instead of an exception.
* **Threading:** `setTorchMode` runs on a background executor; the reply is
  posted back to the Android main thread.
* **Lifecycle:** the torch is switched off when the app goes to the
  background (`onActivityStopped`) and when the engine detaches.
