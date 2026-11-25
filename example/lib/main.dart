import 'package:flutter/material.dart';
import 'package:my_flashlight_plugin/my_flashlight_plugin.dart';

void main() => runApp(const MaterialApp(home: ExamplePage()));

/// Minimal demo: one button that toggles the flashlight.
class ExamplePage extends StatelessWidget {
  const ExamplePage({super.key});

  Future<void> _toggle(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final isOn = await MyFlashlightPlugin.toggle();
      messenger.showSnackBar(
        SnackBar(content: Text(isOn ? 'Flashlight on' : 'Flashlight off')),
      );
    } on FlashlightException catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text('Not available: ${e.error.name}')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('my_flashlight_plugin')),
      body: Center(
        child: Builder(
          builder: (context) => FilledButton.icon(
            onPressed: () => _toggle(context),
            icon: const Icon(Icons.flashlight_on),
            label: const Text('Toggle flashlight'),
          ),
        ),
      ),
    );
  }
}
