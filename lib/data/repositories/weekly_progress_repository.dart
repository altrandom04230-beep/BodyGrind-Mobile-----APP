import 'dart:async';
import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/weekly_record.dart';

/// Weekly progress repository replacing WeeklyProgressRepository.kt
class WeeklyProgressRepository {
  static const String _prefsKey = 'weekly_records_json';

  final _recordsController = StreamController<List<WeeklyRecord>>.broadcast();
  List<WeeklyRecord> _records = [];

  Stream<List<WeeklyRecord>> get records => _recordsController.stream;
  List<WeeklyRecord> get currentRecords => List.unmodifiable(_records);

  WeeklyProgressRepository() {
    loadRecords();
  }

  Future<void> loadRecords() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonStr = prefs.getString(_prefsKey);

    if (jsonStr == null) {
      final seeded = _createInitialSeedRecords();
      _records = seeded;
      _recordsController.add(List.unmodifiable(_records));
      await _persistToPrefs(seeded);
      return;
    }

    try {
      final List<dynamic> list = jsonDecode(jsonStr);
      _records = list.map((e) => WeeklyRecord.fromJson(e as Map<String, dynamic>)).toList();
      _records.sort((a, b) => b.timestamp.compareTo(a.timestamp));
    } catch (_) {
      _records = _createInitialSeedRecords();
    }
    _recordsController.add(List.unmodifiable(_records));
  }

  List<WeeklyRecord> _createInitialSeedRecords() {
    final now = DateTime.now();
    final prevWeekDate = now.subtract(const Duration(days: 7));
    final calendarWeek = _getIsoWeekNumber(prevWeekDate);

    final baseline = WeeklyRecord(
      id: 'seed_week_1',
      weekNumber: 1,
      year: prevWeekDate.year,
      calendarWeek: calendarWeek,
      timestamp: prevWeekDate.millisecondsSinceEpoch,
      memberName: 'Fitness Athlete',
      memberAge: 26,
      weightKg: 71.5,
      heightCm: 175.0,
      weightUnit: 'kg',
      heightUnit: 'cm',
      chestCm: 97.0,
      armCm: 34.5,
      waistCm: 84.0,
      measurementUnit: 'cm',
    );
    return [baseline];
  }

  bool isRecordSavedThisWeek() {
    final now = DateTime.now();
    final currentYear = now.year;
    final currentWeek = _getIsoWeekNumber(now);
    return _records.any((r) => r.year == currentYear && r.calendarWeek == currentWeek);
  }

  WeeklyRecord? getRecordForThisWeek() {
    final now = DateTime.now();
    final currentYear = now.year;
    final currentWeek = _getIsoWeekNumber(now);
    try {
      return _records.firstWhere((r) => r.year == currentYear && r.calendarWeek == currentWeek);
    } catch (_) {
      return null;
    }
  }

  int getNextWeekNumber() {
    if (_records.isEmpty) return 1;
    final maxWeek = _records.map((r) => r.weekNumber).reduce((a, b) => a > b ? a : b);
    return maxWeek + 1;
  }

  Future<bool> saveRecord(WeeklyRecord record) async {
    final list = List<WeeklyRecord>.from(_records);
    final existingIndex = list.indexWhere(
      (r) => r.id == record.id || (r.year == record.year && r.calendarWeek == record.calendarWeek),
    );

    if (existingIndex != -1) {
      list[existingIndex] = record;
    } else {
      list.insert(0, record);
    }
    list.sort((a, b) => b.timestamp.compareTo(a.timestamp));
    _records = list;
    _recordsController.add(List.unmodifiable(_records));
    await _persistToPrefs(list);
    return true;
  }

  Future<void> deleteRecord(String recordId) async {
    _records.removeWhere((r) => r.id == recordId);
    _recordsController.add(List.unmodifiable(_records));
    await _persistToPrefs(_records);
  }

  Future<void> _persistToPrefs(List<WeeklyRecord> list) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonStr = jsonEncode(list.map((r) => r.toJson()).toList());
    await prefs.setString(_prefsKey, jsonStr);
  }

  int _getIsoWeekNumber(DateTime date) {
    final dayOfYear = int.parse(date.difference(DateTime(date.year, 1, 1)).inDays.toString()) + 1;
    return ((dayOfYear - date.weekday + 10) / 7).floor();
  }

  void dispose() {
    _recordsController.close();
  }
}
