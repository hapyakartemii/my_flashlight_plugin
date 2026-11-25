import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_flashlight_plugin/my_flashlight_plugin.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  void mockNative(Object? Function(MethodCall call) handler) {
    messenger.setMockMethodCallHandler(
      MyFlashlightPlugin.channel,
      (call) async => handler(call),
    );
  }

  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    messenger.setMockMethodCallHandler(MyFlashlightPlugin.channel, null);
  });

  test('iOS: toggle throws unsupportedPlatform, channel not called', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    var called = false;
    mockNative((_) => called = true);

    await expectLater(
      MyFlashlightPlugin.toggle(),
      throwsA(
        isA<FlashlightException>().having(
          (e) => e.error,
          'error',
          FlashlightError.unsupportedPlatform,
        ),
      ),
    );
    expect(called, isFalse);
    expect(await MyFlashlightPlugin.isAvailable(), isFalse);
  });

  test('Android: toggle returns the native state', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    mockNative((call) => call.method == 'toggle');

    expect(await MyFlashlightPlugin.toggle(), isTrue);
  });

  test('Android without a flash: NO_FLASH becomes noFlash', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    mockNative((_) => throw PlatformException(code: 'NO_FLASH'));

    await expectLater(
      MyFlashlightPlugin.toggle(),
      throwsA(
        isA<FlashlightException>().having(
          (e) => e.error,
          'error',
          FlashlightError.noFlash,
        ),
      ),
    );
  });
}
