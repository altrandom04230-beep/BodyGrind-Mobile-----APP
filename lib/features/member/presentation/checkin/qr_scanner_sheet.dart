import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../../../core/constants/app_colors.dart';

class QrScannerSheet extends StatefulWidget {
  final ValueChanged<String> onCodeScanned;
  final VoidCallback onDismiss;

  const QrScannerSheet({
    super.key,
    required this.onCodeScanned,
    required this.onDismiss,
  });

  @override
  State<QrScannerSheet> createState() => _QrScannerSheetState();
}

class _QrScannerSheetState extends State<QrScannerSheet> {
  final MobileScannerController _controller = MobileScannerController();
  bool _hasScanned = false;
  final TextEditingController _manualInputController =
      TextEditingController(text: 'BG-CHECKIN:v2:20261027:PKogy4RIHXmsirQTvcQSoZ');

  @override
  void dispose() {
    _controller.dispose();
    _manualInputController.dispose();
    super.dispose();
  }

  void _handleBarcode(BarcodeCapture capture) {
    if (_hasScanned) return;
    final barcode = capture.barcodes.firstOrNull;
    final rawValue = barcode?.rawValue;
    if (rawValue != null && rawValue.isNotEmpty) {
      _hasScanned = true;
      widget.onCodeScanned(rawValue);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: theme.colorScheme.outlineVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Scan Check-in Code',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      'Align the front desk QR display in the frame',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: widget.onDismiss,
                ),
              ],
            ),
            const SizedBox(height: 16),
            ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: SizedBox(
                height: 240,
                width: double.infinity,
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    MobileScanner(
                      controller: _controller,
                      onDetect: _handleBarcode,
                    ),
                    Container(
                      width: 180,
                      height: 180,
                      decoration: BoxDecoration(
                        border: Border.all(color: AppColors.flameOrange, width: 2),
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'Manual / Debug Check-in',
              style: theme.textTheme.labelMedium?.copyWith(
                fontWeight: FontWeight.bold,
                color: theme.colorScheme.primary,
              ),
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _manualInputController,
                    decoration: const InputDecoration(
                      hintText: 'Enter QR Code Payload',
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: () {
                    final code = _manualInputController.text.trim();
                    if (code.isNotEmpty) {
                      widget.onCodeScanned(code);
                    }
                  },
                  child: const Text('Check In'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
