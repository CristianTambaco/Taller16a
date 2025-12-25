import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// DataSource para manejar notificaciones locales
///
/// EXPLICACIÓN DIDÁCTICA:
/// - flutter_local_notifications permite mostrar notificaciones
/// - Útil para recordatorios, alertas, metas alcanzadas
abstract class NotificationDataSource {
  Future<void> initialize();
  Future<void> showStepGoalNotification(int steps);
  Future<void> showFallAlert();
}

class NotificationDataSourceImpl implements NotificationDataSource {
  final FlutterLocalNotificationsPlugin _notifications =
      FlutterLocalNotificationsPlugin();

  @override
  Future<void> initialize() async {
    // Configuración para Android
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');

    // Configuración para iOS
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );

    const settings = InitializationSettings(
      android: androidSettings,
      iOS: iosSettings,
    );

    await _notifications.initialize(settings);
  }

  @override
  Future<void> showStepGoalNotification(int steps) async {
    const androidDetails = AndroidNotificationDetails(
      'fitness_channel',
      'Fitness Notifications',
      channelDescription: 'Notificaciones de metas de fitness',
      importance: Importance.high,
      priority: Priority.high,
      icon: '@mipmap/ic_launcher',
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    const details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _notifications.show(
      1,
      '¡Meta Alcanzada! 🎉',
      'Has completado $steps pasos. ¡Sigue así!',
      details,
    );
  }

  @override
  Future<void> showFallAlert() async {
    const androidDetails = AndroidNotificationDetails(
      'alert_channel',
      'Alertas',
      channelDescription: 'Alertas de seguridad',
      importance: Importance.max,
      priority: Priority.max,
      icon: '@mipmap/ic_launcher',
      enableVibration: true,
      playSound: true,
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    const details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _notifications.show(
      2,
      '⚠️ Caída Detectada',
      'Se ha detectado una posible caída. ¿Estás bien?',
      details,
    );
  }
}
