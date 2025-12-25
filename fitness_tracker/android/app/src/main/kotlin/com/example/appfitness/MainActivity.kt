package com.tuinstituto.fitness_tracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executor
import kotlin.math.sqrt

/**
 * MainActivity: punto de entrada de la aplicación Android
 * - Extiende FlutterFragmentActivity (necesario para BiometricPrompt)
 * - Configura los Platform Channels aquí
 */
class MainActivity: FlutterFragmentActivity() {

    // Definir nombres de canales (DEBEN coincidir con Dart)
    private val BIOMETRIC_CHANNEL = "com.tuinstituto.fitness/biometric"
    private val ACCELEROMETER_CHANNEL = "com.tuinstituto.fitness/accelerometer"
    private val GPS_CHANNEL = "com.tuinstituto.fitness/gps"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    
    // Notificaciones
    private val CHANNEL_ID = "fitness_notifications"
    private val STEP_GOAL_NOTIFICATION_ID = 1
    private val FALL_NOTIFICATION_ID = 2

    // Variables para biometría
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private var pendingResult: MethodChannel.Result? = null

    /**
     * configureFlutterEngine: se llama al iniciar la app
     * AQUÍ configuramos TODOS los Platform Channels
     */
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Inicializar executor para biometría
        executor = ContextCompat.getMainExecutor(this)
        
        // Crear canal de notificaciones
        createNotificationChannel()

        // CONFIGURAR PLATFORM CHANNEL - BIOMETRÍA
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            BIOMETRIC_CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkBiometricSupport" -> {
                    val canAuth = checkBiometricSupport()
                    result.success(canAuth)
                }

                "authenticate" -> {
                    pendingResult = result
                    showBiometricPrompt()
                }

                else -> {
                    result.notImplemented()
                }
            }
        }

        // Configurar canales adicionales
        setupAccelerometerChannel(flutterEngine)
        setupGpsChannel(flutterEngine)
    }

    /**
     * Verificar si el dispositivo soporta biometría
     */
    private fun checkBiometricSupport(): Boolean {
        val biometricManager = BiometricManager.from(this)

        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Mostrar diálogo de autenticación biométrica
     */
    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación Biométrica")
            .setSubtitle("Usa tu huella dactilar")
            .setDescription("Coloca tu dedo en el sensor")
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    // ✅ Autenticación exitosa
                    pendingResult?.success(true)
                    pendingResult = null
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    // ❌ Error en autenticación
                    pendingResult?.success(false)
                    pendingResult = null
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Usuario puede reintentar
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Configurar EventChannel para acelerómetro
     *
     * EXPLICACIÓN DIDÁCTICA:
     * - EventChannel.StreamHandler tiene 2 métodos:
     *   1. onListen: cuando Flutter comienza a escuchar
     *   2. onCancel: cuando Flutter deja de escuchar
     */
    private fun setupAccelerometerChannel(flutterEngine: FlutterEngine) {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var stepCount = 0
        var lastMagnitude = 0.0
        var sensorEventListener: SensorEventListener? = null
        var goalNotificationSent = false

        // Variables para suavizado
        val magnitudeHistory = mutableListOf<Double>()
        val historySize = 10
        var sampleCount = 0
        var lastActivityType = "stationary"
        var activityConfidence = 0
        
        // Variables para detección de caídas
        var lastFallDetectionTime = 0L

        // CONFIGURAR EVENT CHANNEL
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            ACCELEROMETER_CHANNEL
        ).setStreamHandler(object : EventChannel.StreamHandler {

            /**
             * onListen: Flutter comenzó a escuchar el stream
             * AQUÍ iniciamos el sensor
             */
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                sensorEventListener = object : SensorEventListener {

                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            // Calcular magnitud del vector
                            val x = it.values[0]
                            val y = it.values[1]
                            val z = it.values[2]
                            val magnitude = sqrt((x * x + y * y + z * z).toDouble())

                            // Promedio móvil para suavizar
                            magnitudeHistory.add(magnitude)
                            if (magnitudeHistory.size > historySize) {
                                magnitudeHistory.removeAt(0)
                            }
                            val avgMagnitude = magnitudeHistory.average()

                            // DETECCIÓN DE CAÍDAS - Pico de aceleración > 25 m/s²
                            val currentTime = System.currentTimeMillis()
                            if (magnitude > 25.0 && (currentTime - lastFallDetectionTime) > 5000) {
                                // Detectar caída y enviar alerta
                                lastFallDetectionTime = currentTime
                                sendFallAlert()
                                
                                // Enviar evento especial a Flutter
                                val fallData = mapOf(
                                    "type" to "fall_detected",
                                    "magnitude" to magnitude,
                                    "timestamp" to currentTime
                                )
                                events?.success(fallData)
                            }
                            
                            // Detectar paso
                            if (magnitude > 12 && lastMagnitude <= 12) {
                                stepCount++
                                
                                // NOTIFICACIÓN AL ALCANZAR 30 PASOS
                                if (stepCount >= 30 && !goalNotificationSent) {
                                    sendStepGoalNotification(stepCount)
                                    goalNotificationSent = true
                                }
                            }
                            lastMagnitude = magnitude

                            // Determinar actividad (con promedio)
                            val newActivityType = when {
                                avgMagnitude < 10.5 -> "stationary"
                                avgMagnitude < 13.5 -> "walking"
                                else -> "running"
                            }

                            // Solo cambiar si hay confianza
                            if (newActivityType == lastActivityType) {
                                activityConfidence++
                            } else {
                                activityConfidence = 0
                            }

                            val finalActivityType = if (activityConfidence >= 3) {
                                newActivityType
                            } else {
                                lastActivityType
                            }
                            lastActivityType = newActivityType

                            // Enviar cada 3 muestras
                            sampleCount++
                            if (sampleCount >= 3) {
                                sampleCount = 0

                                // ENVIAR DATOS A FLUTTER
                                val data = mapOf(
                                    "stepCount" to stepCount,
                                    "activityType" to finalActivityType,
                                    "magnitude" to avgMagnitude
                                )

                                // events?.success: envía datos al stream
                                events?.success(data)
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                // Registrar listener del sensor
                sensorManager.registerListener(
                    sensorEventListener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }

            /**
             * onCancel: Flutter dejó de escuchar
             * AQUÍ detenemos el sensor
             */
            override fun onCancel(arguments: Any?) {
                sensorEventListener?.let {
                    sensorManager.unregisterListener(it)
                }
                sensorEventListener = null
            }
        })

        // MethodChannel auxiliar para control
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "$ACCELEROMETER_CHANNEL/control"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "start" -> {
                    stepCount = 0
                    goalNotificationSent = false
                    result.success(null)
                }
                "stop" -> {
                    result.success(null)
                }
                "reset" -> {
                    stepCount = 0
                    goalNotificationSent = false
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    /**
     * Configurar canales de GPS
     */
    private fun setupGpsChannel(flutterEngine: FlutterEngine) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var locationListener: LocationListener? = null

        // METHOD CHANNEL - Operaciones puntuales
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            GPS_CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "isGpsEnabled" -> {
                    val isEnabled = locationManager.isProviderEnabled(
                        LocationManager.GPS_PROVIDER
                    )
                    result.success(isEnabled)
                }

                "requestPermissions" -> {
                    if (hasLocationPermission()) {
                        result.success(true)
                    } else {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
                            LOCATION_PERMISSION_REQUEST_CODE
                        )
                        result.success(hasLocationPermission())
                    }
                }

                "getCurrentLocation" -> {
                    if (!hasLocationPermission()) {
                        result.error("PERMISSION_DENIED", "Sin permisos", null)
                        return@setMethodCallHandler
                    }

                    try {
                        val location = locationManager.getLastKnownLocation(
                            LocationManager.GPS_PROVIDER
                        ) ?: locationManager.getLastKnownLocation(
                            LocationManager.NETWORK_PROVIDER
                        )

                        if (location != null) {
                            result.success(locationToMap(location))
                        } else {
                            result.error("NO_LOCATION", "No disponible", null)
                        }
                    } catch (e: SecurityException) {
                        result.error("SECURITY_ERROR", e.message, null)
                    }
                }

                else -> result.notImplemented()
            }
        }

        // EVENT CHANNEL - Stream de ubicaciones
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "$GPS_CHANNEL/stream"
        ).setStreamHandler(object : EventChannel.StreamHandler {

            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                if (!hasLocationPermission()) {
                    events?.error("PERMISSION_DENIED", "Sin permisos", null)
                    return
                }

                locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        // Enviar ubicación a Flutter
                        events?.success(locationToMap(location))
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                try {
                    // Solicitar actualizaciones
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,      // cada 1 segundo
                        0f,         // cualquier distancia
                        locationListener!!
                    )
                } catch (e: SecurityException) {
                    events?.error("SECURITY_ERROR", e.message, null)
                }
            }

            override fun onCancel(arguments: Any?) {
                locationListener?.let {
                    locationManager.removeUpdates(it)
                }
                locationListener = null
            }
        })
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun locationToMap(location: Location): Map<String, Any> {
        return mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "altitude" to location.altitude,
            "speed" to location.speed.toDouble(),
            "accuracy" to location.accuracy.toDouble(),
            "timestamp" to location.time
        )
    }
    
    /**
     * Crear canal de notificaciones (necesario para Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Fitness Notifications"
            val descriptionText = "Notificaciones de metas de pasos y alertas"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Enviar notificación al alcanzar la meta de pasos
     */
    private fun sendStepGoalNotification(steps: Int) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("¡Meta Alcanzada! 🎉")
            .setContentText("Has completado $steps pasos. ¡Sigue así!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(STEP_GOAL_NOTIFICATION_ID, builder.build())
    }
    
    /**
     * Enviar alerta de caída detectada
     */
    private fun sendFallAlert() {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Caída Detectada")
            .setContentText("Se ha detectado una posible caída. ¿Estás bien?")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FALL_NOTIFICATION_ID, builder.build())
    }
}
