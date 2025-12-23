import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as tz;
import 'package:timezone/timezone.dart' as tz;

/// DataSource para notificaciones usando flutter_local_notifications
abstract class NotificationDataSource {
  Future<void> initialize();
  Future<void> showStepGoalNotification(int stepCount);
  Future<void> showFallNotification();
}

class NotificationDataSourceImpl implements NotificationDataSource {
  final FlutterLocalNotificationsPlugin _notificationsPlugin =
      FlutterLocalNotificationsPlugin();

  @override
  Future<void> initialize() async {
    tz.initializeTimeZones();

    // Configuración para Android
    const AndroidInitializationSettings initializationSettingsAndroid =
        AndroidInitializationSettings('@mipmap/ic_launcher');

    // Configuración para iOS/macOS
    const DarwinInitializationSettings initializationSettingsDarwin =
        DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );

    final InitializationSettings initializationSettings =
        InitializationSettings(
      android: initializationSettingsAndroid,
      iOS: initializationSettingsDarwin,
      macOS: initializationSettingsDarwin,
    );

    await _notificationsPlugin.initialize(
      initializationSettings,
      onDidReceiveNotificationResponse: (NotificationResponse response) {
        // Manejar acción al tocar la notificación
        print('Notificación tocada: ${response.payload}');
      },
    );
  }

  @override
  Future<void> showFallNotification() async {
    const AndroidNotificationDetails androidPlatformChannelSpecifics =
        AndroidNotificationDetails(
      'fall_alert_channel_v2', // id actualizado para forzar refresh de settings
      'Fall Detection Alerts', // name
      channelDescription: 'Critical alerts for detected falls',
      importance: Importance.max,
      priority: Priority.high,
      ticker: 'ticker',
      enableVibration: true,
      playSound: true,
      fullScreenIntent: true, // Para mayor visibilidad en alertas críticas
    );

    const NotificationDetails platformChannelSpecifics =
        NotificationDetails(android: androidPlatformChannelSpecifics);

    await _notificationsPlugin.show(
      1, // id
      '⚠️ ¡Caída Detectada!', // title
      'Se ha detectado un impacto fuerte (> 25 m/s²). ¿Estás bien?', // body
      platformChannelSpecifics,
      payload: 'fall_alert',
    );
  }

  @override
  Future<void> showStepGoalNotification(int stepCount) async {
    const AndroidNotificationDetails androidPlatformChannelSpecifics =
        AndroidNotificationDetails(
      'step_goal_channel_v2', // id actualizado
      'Step Goal Notifications', // name
      channelDescription: 'Notifications for reaching step goals',
      importance: Importance.max,
      priority: Priority.high,
      ticker: 'ticker',
      enableVibration: true,
      playSound: true,
    );

    const NotificationDetails platformChannelSpecifics =
        NotificationDetails(android: androidPlatformChannelSpecifics);

    await _notificationsPlugin.show(
      0, // id
      '¡Objetivo Alcanzado!', // title
      'Has superado los $stepCount pasos. ¡Sigue así!', // body
      platformChannelSpecifics,
      payload: 'step_goal',
    );
  }
}
