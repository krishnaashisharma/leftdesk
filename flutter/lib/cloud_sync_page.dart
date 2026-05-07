import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Cloud Storage Sync Settings Page
/// Allows connecting Google Drive or OneDrive to continuously upload
/// LOGGERSSS logs to the cloud.
class CloudSyncPage extends StatefulWidget {
  const CloudSyncPage({Key? key}) : super(key: key);

  @override
  State<CloudSyncPage> createState() => _CloudSyncPageState();
}

class _CloudSyncPageState extends State<CloudSyncPage> {
  static const _platform = MethodChannel('com.leftdesk.app/cloud_sync');

  String _provider = 'none';
  bool _syncEnabled = false;
  String _lastStatus = 'Never synced';
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _loadStatus();
  }

  Future<void> _loadStatus() async {
    try {
      final result = await _platform.invokeMethod<Map>('getSyncStatus');
      setState(() {
        _provider = result?['provider'] as String? ?? 'none';
        _syncEnabled = result?['enabled'] as bool? ?? false;
        _lastStatus = result?['lastStatus'] as String? ?? 'Never synced';
      });
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cloud Storage Sync'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _triggerSync,
            tooltip: 'Sync now',
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildInfoCard(),
            const SizedBox(height: 16),
            _buildProviderSection(),
            const SizedBox(height: 16),
            _buildStatusCard(),
            if (_provider != 'none') ...[
              const SizedBox(height: 16),
              _buildSyncControlCard(),
            ],
            const SizedBox(height: 16),
            _buildDisconnectButton(),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoCard() {
    return Card(
      color: Colors.blue.shade50,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            Icon(Icons.cloud_upload, color: Colors.blue),
            SizedBox(width: 8),
            Expanded(
              child: Text(
                'LOGGERSSS logs are automatically uploaded to your cloud storage '
                'every 15 minutes when connected to the internet. '
                'Each log file is stored in a LOGGERSSS folder in your cloud drive.',
                style: TextStyle(fontSize: 13, height: 1.4),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildProviderSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Connect Cloud Storage',
            style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _ProviderCard(
                icon: Icons.cloud,
                color: Colors.blue,
                title: 'Google Drive',
                subtitle: 'Upload to LOGGERSSS folder\nin your Google Drive',
                selected: _provider == 'google_drive',
                onTap: _loading ? null : () => _connectGoogle(),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _ProviderCard(
                icon: Icons.cloud_queue,
                color: Colors.indigo,
                title: 'OneDrive',
                subtitle: 'Upload to LOGGERSSS folder\nin your OneDrive',
                selected: _provider == 'onedrive',
                onTap: _loading ? null : () => _connectOneDrive(),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildStatusCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Icon(
              _provider == 'none' ? Icons.cloud_off : Icons.cloud_done,
              color: _provider == 'none' ? Colors.grey : Colors.green,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _provider == 'none'
                        ? 'Not connected'
                        : _provider == 'google_drive'
                            ? 'Google Drive connected'
                            : 'OneDrive connected',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  Text(_lastStatus,
                      style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
                ],
              ),
            ),
            if (_loading)
              const SizedBox(
                width: 20, height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildSyncControlCard() {
    return Card(
      child: SwitchListTile(
        secondary: const Icon(Icons.sync),
        title: const Text('Auto Sync'),
        subtitle: const Text('Upload logs every 15 minutes'),
        value: _syncEnabled,
        onChanged: (val) => _toggleSync(val),
      ),
    );
  }

  Widget _buildDisconnectButton() {
    if (_provider == 'none') return const SizedBox.shrink();
    return OutlinedButton.icon(
      onPressed: _loading ? null : _disconnect,
      icon: const Icon(Icons.link_off, color: Colors.red),
      label: const Text('Disconnect Cloud Storage',
          style: TextStyle(color: Colors.red)),
      style: OutlinedButton.styleFrom(
        side: const BorderSide(color: Colors.red),
      ),
    );
  }

  Future<void> _connectGoogle() async {
    setState(() => _loading = true);
    try {
      await _platform.invokeMethod('connectGoogleDrive');
      await Future.delayed(const Duration(seconds: 2));
      await _loadStatus();
    } catch (e) {
      _showSnack('Failed to open Google sign-in: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _connectOneDrive() async {
    setState(() => _loading = true);
    try {
      await _platform.invokeMethod('connectOneDrive');
      await Future.delayed(const Duration(seconds: 2));
      await _loadStatus();
    } catch (e) {
      _showSnack('Failed to open OneDrive sign-in: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _toggleSync(bool enable) async {
    try {
      await _platform.invokeMethod('setSyncEnabled', {'enabled': enable});
      setState(() => _syncEnabled = enable);
    } catch (e) {
      _showSnack('Error: $e');
    }
  }

  Future<void> _triggerSync() async {
    setState(() => _loading = true);
    try {
      await _platform.invokeMethod('triggerSync');
      _showSnack('Sync started — check status in a moment');
      await Future.delayed(const Duration(seconds: 3));
      await _loadStatus();
    } catch (e) {
      _showSnack('Sync error: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _disconnect() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Disconnect Cloud Storage?'),
        content: const Text(
            'Auto-sync will be disabled. Existing uploaded logs will not be deleted.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Disconnect', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (confirm == true) {
      await _platform.invokeMethod('disconnectCloud');
      setState(() {
        _provider = 'none';
        _syncEnabled = false;
        _lastStatus = 'Disconnected';
      });
    }
  }

  void _showSnack(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _ProviderCard extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final String subtitle;
  final bool selected;
  final VoidCallback? onTap;

  const _ProviderCard({
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.selected,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: selected ? color.withOpacity(0.12) : Colors.grey.shade50,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: selected ? color : Colors.grey.shade300,
            width: selected ? 2 : 1,
          ),
        ),
        child: Column(
          children: [
            Icon(icon, color: selected ? color : Colors.grey, size: 32),
            const SizedBox(height: 8),
            Text(title,
                style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: selected ? color : Colors.black87)),
            const SizedBox(height: 4),
            Text(subtitle,
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
            if (selected) ...[
              const SizedBox(height: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Text('Connected',
                    style: TextStyle(color: Colors.white, fontSize: 10)),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
