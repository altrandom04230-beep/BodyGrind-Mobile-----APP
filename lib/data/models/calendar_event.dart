/// Calendar event and exception models matching CalendarEntities.kt
class CalendarEvent {
  final int id;
  final String? planId;
  final String title;
  final String startDate; // yyyy-MM-dd
  final int timeHour;
  final int timeMinute;
  final String repeatType; // NONE, DAILY, WEEKLY
  final int repeatDaysMask;
  final String? repeatUntil;
  final int reminderMinutesBefore;
  final bool isEnabled;
  final int createdAt;
  final String? note;

  const CalendarEvent({
    this.id = 0,
    this.planId,
    required this.title,
    required this.startDate,
    required this.timeHour,
    required this.timeMinute,
    required this.repeatType,
    this.repeatDaysMask = 0,
    this.repeatUntil,
    this.reminderMinutesBefore = -1,
    this.isEnabled = true,
    required this.createdAt,
    this.note,
  });

  CalendarEvent copyWith({
    int? id,
    String? planId,
    String? title,
    String? startDate,
    int? timeHour,
    int? timeMinute,
    String? repeatType,
    int? repeatDaysMask,
    String? repeatUntil,
    int? reminderMinutesBefore,
    bool? isEnabled,
    int? createdAt,
    String? note,
  }) {
    return CalendarEvent(
      id: id ?? this.id,
      planId: planId ?? this.planId,
      title: title ?? this.title,
      startDate: startDate ?? this.startDate,
      timeHour: timeHour ?? this.timeHour,
      timeMinute: timeMinute ?? this.timeMinute,
      repeatType: repeatType ?? this.repeatType,
      repeatDaysMask: repeatDaysMask ?? this.repeatDaysMask,
      repeatUntil: repeatUntil ?? this.repeatUntil,
      reminderMinutesBefore: reminderMinutesBefore ?? this.reminderMinutesBefore,
      isEnabled: isEnabled ?? this.isEnabled,
      createdAt: createdAt ?? this.createdAt,
      note: note ?? this.note,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      if (id > 0) 'id': id,
      'planId': planId,
      'title': title,
      'startDate': startDate,
      'timeHour': timeHour,
      'timeMinute': timeMinute,
      'repeatType': repeatType,
      'repeatDaysMask': repeatDaysMask,
      'repeatUntil': repeatUntil,
      'reminderMinutesBefore': reminderMinutesBefore,
      'isEnabled': isEnabled ? 1 : 0,
      'createdAt': createdAt,
      'note': note,
    };
  }

  factory CalendarEvent.fromMap(Map<String, dynamic> map) {
    return CalendarEvent(
      id: (map['id'] as num?)?.toInt() ?? 0,
      planId: map['planId'] as String?,
      title: map['title'] as String,
      startDate: map['startDate'] as String,
      timeHour: (map['timeHour'] as num).toInt(),
      timeMinute: (map['timeMinute'] as num).toInt(),
      repeatType: map['repeatType'] as String,
      repeatDaysMask: (map['repeatDaysMask'] as num?)?.toInt() ?? 0,
      repeatUntil: map['repeatUntil'] as String?,
      reminderMinutesBefore: (map['reminderMinutesBefore'] as num?)?.toInt() ?? -1,
      isEnabled: (map['isEnabled'] as num?) == 1,
      createdAt: (map['createdAt'] as num?)?.toInt() ?? DateTime.now().millisecondsSinceEpoch,
      note: map['note'] as String?,
    );
  }
}

class CalendarEventException {
  final int id;
  final int eventId;
  final String occurrenceDate;
  final String type; // SKIPPED

  const CalendarEventException({
    this.id = 0,
    required this.eventId,
    required this.occurrenceDate,
    this.type = 'SKIPPED',
  });

  Map<String, dynamic> toMap() {
    return {
      if (id > 0) 'id': id,
      'eventId': eventId,
      'occurrenceDate': occurrenceDate,
      'type': type,
    };
  }

  factory CalendarEventException.fromMap(Map<String, dynamic> map) {
    return CalendarEventException(
      id: (map['id'] as num?)?.toInt() ?? 0,
      eventId: (map['eventId'] as num).toInt(),
      occurrenceDate: map['occurrenceDate'] as String,
      type: map['type'] as String? ?? 'SKIPPED',
    );
  }
}
