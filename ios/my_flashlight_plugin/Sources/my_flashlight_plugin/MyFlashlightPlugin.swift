import Flutter
import UIKit

/// iOS stub: the flashlight is implemented for Android only.
/// The Dart side never calls the channel on iOS; if it ever does,
/// it gets an UNSUPPORTED error instead of a crash.
public class MyFlashlightPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(
      name: "my_flashlight_plugin", binaryMessenger: registrar.messenger())
    registrar.addMethodCallDelegate(MyFlashlightPlugin(), channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result(
      FlutterError(
        code: "UNSUPPORTED", message: "Flashlight is Android-only.", details: nil))
  }
}
