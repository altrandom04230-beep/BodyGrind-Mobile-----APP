import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import '../../../../core/constants/app_colors.dart';

class BmiTrendChart extends StatelessWidget {
  final List<double> values;
  final List<String> labels;
  final Color lineColor;
  final String title;

  const BmiTrendChart({
    super.key,
    required this.values,
    required this.labels,
    this.lineColor = AppColors.boldRed,
    this.title = 'Trend',
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (values.length < 2) {
      return Container(
        height: 160,
        alignment: Alignment.center,
        child: Text(
          'Log at least 2 entries to see $title trends',
          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
      );
    }

    final minY = values.reduce((a, b) => a < b ? a : b) - 2;
    final maxY = values.reduce((a, b) => a > b ? a : b) + 2;

    final spots = <FlSpot>[];
    for (int i = 0; i < values.length; i++) {
      spots.add(FlSpot(i.toDouble(), values[i]));
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: theme.textTheme.labelMedium?.copyWith(
            fontWeight: FontWeight.bold,
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 10),
        SizedBox(
          height: 160,
          child: LineChart(
            LineChartData(
              gridData: FlGridData(
                show: true,
                drawVerticalLine: false,
                getDrawingHorizontalLine: (val) => FlLine(
                  color: theme.colorScheme.outlineVariant.withOpacity(0.3),
                  strokeWidth: 1,
                ),
              ),
              titlesData: FlTitlesData(
                show: true,
                rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                bottomTitles: AxisTitles(
                  sideTitles: SideTitles(
                    showTitles: true,
                    reservedSize: 22,
                    interval: 1,
                    getTitlesWidget: (val, meta) {
                      final idx = val.toInt();
                      if (idx >= 0 && idx < labels.length) {
                        return Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(labels[idx], style: const TextStyle(fontSize: 10, color: Colors.grey)),
                        );
                      }
                      return const SizedBox.shrink();
                    },
                  ),
                ),
                leftTitles: AxisTitles(
                  sideTitles: SideTitles(
                    showTitles: true,
                    reservedSize: 36,
                    getTitlesWidget: (val, meta) {
                      return Text(val.toStringAsFixed(1), style: const TextStyle(fontSize: 10, color: Colors.grey));
                    },
                  ),
                ),
              ),
              borderData: FlBorderData(show: false),
              minX: 0,
              maxX: (values.length - 1).toDouble(),
              minY: minY > 0 ? minY : 0,
              maxY: maxY,
              lineBarsData: [
                LineChartBarData(
                  spots: spots,
                  isCurved: true,
                  color: lineColor,
                  barWidth: 3,
                  isStrokeCapRound: true,
                  dotData: FlDotData(
                    show: true,
                    getDotPainter: (spot, percent, barData, index) {
                      return FlDotCirclePainter(
                        radius: 4,
                        color: lineColor,
                        strokeWidth: 2,
                        strokeColor: theme.colorScheme.surface,
                      );
                    },
                  ),
                  belowBarData: BarAreaData(
                    show: true,
                    color: lineColor.withOpacity(0.12),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
