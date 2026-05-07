import 'package:flutter/material.dart';

/// Parental Control Settings Page for LeftDesk
/// All monitoring is FULLY DISCLOSED — requires explicit consent to activate.
/// A permanent system notification is shown while monitoring is active.
class ParentalControlPage extends StatefulWidget {
  const ParentalControlPage({Key? key}) : super(key: key);

  @override
  State<ParentalControlPage> createState() => _ParentalControlPageState();
}

class _ParentalControlPageState extends State<ParentalControlPage> {
  bool _enabled = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Parental Control')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 8),
            Row(
              children: [
                const Icon(Icons.family_restroom, size: 40, color: Colors.blue),
                const SizedBox(width: 12),
                Text(
                  'Parental Control Monitoring',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Text(
              'When enabled, LeftDesk logs remote session activity for parental oversight. '
              'A permanent notification is always visible on this device so the user knows '
              'monitoring is active. All logs are stored only on this device and are never uploaded.',
              style: TextStyle(fontSize: 14, height: 1.5),
            ),
            const SizedBox(height: 16),
            Card(
              color: Colors.amber.shade50,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: const [
                    Icon(Icons.info_outline, color: Colors.orange, size: 20),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Transparency guarantee: The device user will always see a visible '
                        'notification while monitoring is active, and can disable it at any time.',
                        style: TextStyle(fontSize: 13, height: 1.4),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),
            Card(
              child: SwitchListTile(
                secondary: const Icon(Icons.monitor_heart_outlined),
                title: const Text('Enable Activity Monitoring'),
                subtitle: const Text('Requires explicit consent — device user will be notified'),
                value: _enabled,
                onChanged: (val) => val ? _showConsentDialog() : _disableMonitoring(),
              ),
            ),
            if (_enabled) ...[
              const SizedBox(height: 12),
              Card(
                color: Colors.green.shade50,
                child: const Padding(
                  padding: EdgeInsets.all(12),
                  child: Row(
                    children: [
                      Icon(Icons.check_circle, color: Colors.green),
                      SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Monitoring is active. A notification is shown on this device at all times.',
                          style: TextStyle(color: Colors.green, fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _disableMonitoring() {
    setState(() => _enabled = false);
    // Notify native layer to stop ParentalControlService
    // (handled via platform channel in production integration)
  }

  void _showConsentDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.privacy_tip, color: Colors.blue),
            SizedBox(width: 8),
            Text('Consent Required'),
          ],
        ),
        content: const SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'By enabling parental control monitoring, you confirm that:',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              SizedBox(height: 10),
              _BulletPoint('Remote session events will be logged to a file on this device.'),
              _BulletPoint('A visible notification will appear at all times while monitoring is active.'),
              _BulletPoint('The device user can see this notification and disable monitoring.'),
              _BulletPoint('No data is sent off-device — logs remain local only.'),
              _BulletPoint('You can disable monitoring at any time from Settings.'),
              SizedBox(height: 12),
              Text(
                'Intended use: Parents monitoring a child\'s device with the child\'s knowledge. '
                'Covert monitoring without disclosure may violate privacy laws.',
                style: TextStyle(fontStyle: FontStyle.italic, fontSize: 12, color: Colors.grey),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(ctx);
              setState(() => _enabled = true);
              // Notify native layer to start ParentalControlService
            },
            child: const Text('I Understand & Consent'),
          ),
        ],
      ),
    );
  }
}

class _BulletPoint extends StatelessWidget {
  final String text;
  const _BulletPoint(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('• ', style: TextStyle(fontSize: 14)),
          Expanded(child: Text(text, style: const TextStyle(fontSize: 13))),
        ],
      ),
    );
  }
}
