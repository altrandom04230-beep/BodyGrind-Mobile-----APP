import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/bmi_record.dart';
import '../../../../data/providers/app_providers.dart';
import 'bmi_trend_chart.dart';
import 'package:intl/intl.dart';

class BmiCalculatorScreen extends ConsumerStatefulWidget {
  const BmiCalculatorScreen({super.key});

  @override
  ConsumerState<BmiCalculatorScreen> createState() => _BmiCalculatorScreenState();
}

class _BmiCalculatorScreenState extends ConsumerState<BmiCalculatorScreen> {
  bool _useMetric = true;
  final TextEditingController _weightController = TextEditingController();
  final TextEditingController _heightController = TextEditingController();
  final TextEditingController _feetController = TextEditingController();
  final TextEditingController _inchesController = TextEditingController();

  double? _calculatedBmi;
  String? _bmiCategory;
  Color? _categoryColor;
  String? _recommendation;
  double? _healthyMinWeight;
  double? _healthyMaxWeight;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    // Prefill from current profile if available
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final profile = ref.read(userProfileStreamProvider).valueOrNull ??
          ref.read(workoutRepositoryProvider).currentProfile;
      setState(() {
        _useMetric = profile.useMetric;
        if (_useMetric) {
          _weightController.text = profile.weightKg.toStringAsFixed(1);
          _heightController.text = profile.heightCm.toStringAsFixed(0);
        } else {
          final lb = profile.weightKg / 0.45359237;
          _weightController.text = lb.toStringAsFixed(1);
          final totalInches = profile.heightCm / 2.54;
          final feet = (totalInches / 12).floor();
          final inches = (totalInches - feet * 12).round();
          _feetController.text = feet.toString();
          _inchesController.text = inches.toString();
        }
      });
    });
  }

  @override
  void dispose() {
    _weightController.dispose();
    _heightController.dispose();
    _feetController.dispose();
    _inchesController.dispose();
    super.dispose();
  }

  void _calculate() {
    setState(() {
      _errorMessage = null;
    });

    double heightCm;
    double weightKg;

    if (_useMetric) {
      final h = double.tryParse(_heightController.text.trim());
      final w = double.tryParse(_weightController.text.trim());
      if (h == null || h <= 0) {
        setState(() => _errorMessage = 'Please enter a valid height (cm).');
        return;
      }
      if (w == null || w <= 0) {
        setState(() => _errorMessage = 'Please enter a valid weight (kg).');
        return;
      }
      heightCm = h;
      weightKg = w;
    } else {
      final ft = double.tryParse(_feetController.text.trim()) ?? 0;
      final inch = double.tryParse(_inchesController.text.trim()) ?? 0;
      final wLb = double.tryParse(_weightController.text.trim());
      if ((ft == 0 && inch == 0) || ft < 0 || inch < 0) {
        setState(() => _errorMessage = 'Please enter valid feet and inches.');
        return;
      }
      if (wLb == null || wLb <= 0) {
        setState(() => _errorMessage = 'Please enter a valid weight (lb).');
        return;
      }
      final totalInches = (ft * 12) + inch;
      heightCm = totalInches * 2.54;
      weightKg = wLb * 0.45359237;
    }

    if (heightCm < 50 || heightCm > 260) {
      setState(() => _errorMessage = 'Height must be between 50 and 260 cm.');
      return;
    }
    if (weightKg < 20 || weightKg > 350) {
      setState(() => _errorMessage = 'Weight must be between 20 and 350 kg.');
      return;
    }

    final heightM = heightCm / 100.0;
    final bmi = double.parse((weightKg / (heightM * heightM)).toStringAsFixed(1));
    final minHealthy = double.parse((18.5 * heightM * heightM).toStringAsFixed(1));
    final maxHealthy = double.parse((24.9 * heightM * heightM).toStringAsFixed(1));

    String category;
    Color color;
    String rec;

    if (bmi < 18.5) {
      category = 'Underweight';
      color = AppColors.goldYellow;
      rec = 'Nutrient-dense caloric surplus and progressive resistance training are recommended to build lean muscle mass.';
    } else if (bmi < 25.0) {
      category = 'Normal weight';
      color = AppColors.successGreen;
      rec = 'You are within the healthy standard range. Maintain your consistent training volume and balanced nutrition.';
    } else if (bmi < 30.0) {
      category = 'Overweight';
      color = AppColors.boldRed;
      rec = 'Consider modest caloric deficit coupled with resistance circuits or HIIT to reduce body fat while preserving muscle.';
    } else {
      category = 'Obesity';
      color = AppColors.dangerRed;
      rec = 'Focus on low-impact cardiovascular routines, balanced whole foods, and consult our coaches for a structured program.';
    }

    setState(() {
      _calculatedBmi = bmi;
      _bmiCategory = category;
      _categoryColor = color;
      _recommendation = rec;
      _healthyMinWeight = minHealthy;
      _healthyMaxWeight = maxHealthy;
    });

    // Save record to database
    final now = DateTime.now();
    final record = BmiRecord(
      id: '${now.millisecondsSinceEpoch}',
      heightCm: heightCm,
      weightKg: weightKg,
      bmi: bmi,
      category: category,
      timestamp: now.millisecondsSinceEpoch,
      syncedToFirebase: false,
    );
    ref.read(workoutRepositoryProvider).addBmiRecord(record);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('BMI Calculator', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Unit Switcher
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Units System',
                  style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                ),
                SegmentedButton<bool>(
                  segments: const [
                    ButtonSegment(value: true, label: Text('Metric')),
                    ButtonSegment(value: false, label: Text('Imperial')),
                  ],
                  selected: {_useMetric},
                  onSelectionChanged: (val) {
                    setState(() {
                      _useMetric = val.first;
                      _errorMessage = null;
                    });
                  },
                ),
              ],
            ),
            const SizedBox(height: 16),
            // Input Fields Card
            Card(
              elevation: 0,
              shape: RoundedCornerShape(16),
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (_useMetric) ...[
                      TextField(
                        controller: _heightController,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(
                          labelText: 'Height',
                          suffixText: 'cm',
                          prefixIcon: Icon(Icons.height),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _weightController,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(
                          labelText: 'Weight',
                          suffixText: 'kg',
                          prefixIcon: Icon(Icons.monitor_weight_outlined),
                        ),
                      ),
                    ] else ...[
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _feetController,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(
                                labelText: 'Feet',
                                suffixText: 'ft',
                                prefixIcon: Icon(Icons.height),
                              ),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: TextField(
                              controller: _inchesController,
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(
                                labelText: 'Inches',
                                suffixText: 'in',
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _weightController,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(
                          labelText: 'Weight',
                          suffixText: 'lb',
                          prefixIcon: Icon(Icons.monitor_weight_outlined),
                        ),
                      ),
                    ],
                    if (_errorMessage != null) ...[
                      const SizedBox(height: 10),
                      Text(
                        _errorMessage!,
                        style: const TextStyle(color: AppColors.dangerRed, fontSize: 12),
                      ),
                    ],
                    const SizedBox(height: 16),
                    SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.boldRed,
                        ),
                        onPressed: _calculate,
                        icon: const Icon(Icons.calculate),
                        label: const Text('CALCULATE BMI', style: TextStyle(fontWeight: FontWeight.bold)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (_calculatedBmi != null) ...[
              const SizedBox(height: 20),
              // Results Card
              Card(
                elevation: 0,
                shape: RoundedCornerShape(20),
                color: theme.colorScheme.surface,
                child: Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: _categoryColor?.withOpacity(0.5) ?? theme.colorScheme.outline),
                  ),
                  child: Column(
                    children: [
                      Text(
                        'Your BMI Result',
                        style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _calculatedBmi!.toStringAsFixed(1),
                        style: theme.textTheme.displayMedium?.copyWith(
                          fontWeight: FontWeight.black,
                          color: _categoryColor,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                        decoration: BoxDecoration(
                          color: _categoryColor?.withOpacity(0.15),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          _bmiCategory!,
                          style: TextStyle(
                            color: _categoryColor,
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      const Divider(),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('Healthy Weight Range', style: theme.textTheme.bodySmall),
                          Text(
                            _useMetric
                                ? '$_healthyMinWeight - $_healthyMaxWeight kg'
                                : '${(_healthyMinWeight! / 0.45359237).toStringAsFixed(1)} - ${(_healthyMaxWeight! / 0.45359237).toStringAsFixed(1)} lb',
                            style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          _recommendation!,
                          style: theme.textTheme.bodySmall?.copyWith(height: 1.4),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(height: 16),
            // Historical BMI Trend Chart
            ref.watch(bmiRecordsStreamProvider).when(
                  data: (records) {
                    if (records.length < 2) return const SizedBox.shrink();
                    final sorted = List.of(records)..sort((a, b) => a.timestamp.compareTo(b.timestamp));
                    final vals = sorted.map((r) => r.bmi).toList();
                    final dates = sorted.map((r) => DateFormat('M/d').format(DateTime.fromMillisecondsSinceEpoch(r.timestamp))).toList();
                    return Card(
                      elevation: 0,
                      shape: RoundedCornerShape(16),
                      color: theme.colorScheme.surface,
                      child: Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: theme.colorScheme.outlineVariant),
                        ),
                        child: BmiTrendChart(
                          title: 'Historical BMI Trend',
                          values: vals,
                          labels: dates,
                          lineColor: AppColors.boldRed,
                        ),
                      ),
                    );
                  },
                  loading: () => const SizedBox.shrink(),
                  error: (_, __) => const SizedBox.shrink(),
                ),
            const SizedBox(height: 20),
            // Clinical notice
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  Icon(Icons.info_outline, size: 18, color: theme.colorScheme.onSurfaceVariant),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'BMI is a screening tool and does not distinguish between lean muscle mass and fat tissue. Consult certified trainers for tailored guidance.',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                        fontSize: 11,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
