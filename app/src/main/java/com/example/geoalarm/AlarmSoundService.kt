package com.example.geoalarm

// Импорты для уведомлений (сервис должен показывать уведомление в статус-баре)
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

// Импорты для работы сервиса
import android.app.Service
import android.content.Context
import android.content.Intent

// Импорты для звука
import android.media.MediaPlayer

// Импорты для работы с версиями Android
import android.os.Build
import android.os.IBinder

// Импорты для вибрации
import android.os.VibrationEffect
import android.os.Vibrator

// Импорт для красивого уведомления
import androidx.core.app.NotificationCompat

/**
 * AlarmSoundService - СЕРВИС ВОСПРОИЗВЕДЕНИЯ ЗВУКА
 *
 * ЧТО ЭТО:
 * Сервис - компонент Android для работы в фоне (без экрана)
 *
 * ЗАЧЕМ:
 * 1. Играет звук будильника (бесконечно, пока не выключат)
 * 2. Вибрирует телефон (прерывисто)
 * 3. Работает как Foreground Service (с уведомлением), чтобы Android не убил его
 *
 * КАК ИСПОЛЬЗУЕТСЯ:
 * - Запускается из AlarmReceiver когда срабатывает будильник
 * - Останавливается из GeoAlarmActivity когда пользователь приехал или нажал 3 раза
 */
class AlarmSoundService : Service() {

    // ========== ПЕРЕМЕННЫЕ ==========

    private var mediaPlayer: MediaPlayer? = null   // Проигрыватель звука (MP3, WAV и т.д.)
    private lateinit var vibrator: Vibrator         // Вибратор телефона
    private var isRunning = true                    // Флаг для цикла вибрации (работает пока true)

    companion object {
        @Volatile  // @Volatile - чтобы изменения флага были видны из других потоков
        var isActive = false  // Публичный флаг: играет ли звук? (проверяется из AlarmReceiver)
    }

    /**
     * onStartCommand - вызывается когда сервис запускается через startService()
     * @return START_STICKY - если сервис убьют, система попробует перезапустить его
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // ===== 1. ВСТАЁМ =====
        isActive = true   // Говорим другим классам: "Звук играет!"
        isRunning = true  // Запускаем цикл вибрации

        // ===== 2. СОЗДАЁМ УВЕДОМЛЕНИЕ (Foreground Service) =====
        // Foreground Service - сервис с постоянным уведомлением
        // Без этого Android убьёт сервис через несколько секунд на версиях 8+
        createNotificationChannel()           // Создаём канал уведомления (для Android 8+)
        startForeground(1002, createNotification())  // Запускаем foreground режим

        // ===== 3. ЗАПУСКАЕМ ЗВУК В ОТДЕЛЬНОМ ПОТОКЕ =====
        // Отдельный поток нужен чтобы не блокировать основной поток (UI)
        Thread {
            try {
                // Пытаемся найти свой звук в папке res/raw/alarm.mp3
                val soundId = resources.getIdentifier("alarm", "raw", packageName)

                if (soundId != 0) {
                    // Свой звук найден - используем его
                    mediaPlayer = MediaPlayer.create(this, soundId)
                } else {
                    // Своего звука нет - берём системный звук будильника
                    mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
                }

                mediaPlayer?.isLooping = true   // Зацикливаем звук (повторять бесконечно)
                mediaPlayer?.start()            // Начинаем проигрывание

            } catch (e: Exception) {
                // Если что-то пошло не так - используем системный звук как запасной
                mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            }
        }.start()  // Запускаем поток

        // ===== 4. ЗАПУСКАЕМ ВИБРАЦИЮ В ОТДЕЛЬНОМ ПОТОКЕ =====
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        startVibration()  // Запускаем вибрацию в отдельном потоке (см. ниже)

        // START_STICKY - если систему убьёт сервис, она перезапустит его
        return START_STICKY
    }

    /**
     * startVibration - запускает бесконечную прерывистую вибрацию
     * Работает в отдельном потоке, чтобы не блокировать основной
     */
    private fun startVibration() {
        Thread {
            // Цикл работает пока isRunning = true
            while (isRunning) {
                try {
                    // Создаём вибрацию: 500мс вибрация, 500мс пауза, 500мс вибрация, 500мс пауза...
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Для Android 8+ (новый API)
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(500, 500, 500, 500), 0))
                    } else {
                        // Для старых версий
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(500, 500, 500, 500), 0)
                    }

                    // Ждём 2 секунды перед повторением (чтобы не накладывалось)
                    Thread.sleep(2000)

                } catch (e: Exception) {
                    break  // Если ошибка - выходим из цикла
                }
            }
        }.start()
    }

    /**
     * Создаёт канал для уведомлений (обязательно для Android 8+)
     * Без канала уведомление не покажется
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alarm_sound",                    // ID канала (должен совпадать с уведомлением)
                "Звук будильника",                // Название (видит пользователь)
                NotificationManager.IMPORTANCE_HIGH  // Высокий приоритет
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)  // Регистрируем канал
        }
    }

    /**
     * Создаёт уведомление, которое висит в статус-баре пока играет будильник
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "alarm_sound")
            .setContentTitle("🔔 Будильник")           // Заголовок
            .setContentText("Звучит будильник")        // Текст
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)  // Иконка будильника
            .setPriority(NotificationCompat.PRIORITY_MAX)  // Максимальный приоритет
            .build()
    }

    /**
     * stopAlarm - публичный метод для остановки будильника
     * Вызывается из GeoAlarmActivity когда:
     * - Пользователь приехал в зону
     * - Пользователь нажал 3 раза на кнопку
     */
    fun stopAlarm() {
        isRunning = false   // Останавливаем цикл вибрации
        isActive = false    // Сигналим другим классам: "Звук больше не играет"

        // Останавливаем и освобождаем MediaPlayer
        mediaPlayer?.apply {
            if (isPlaying) stop()   // Если играет - остановить
            release()               // Освободить ресурсы (важно!)
        }
        mediaPlayer = null

        vibrator.cancel()   // Останавливаем вибрацию
        stopSelf()          // Останавливаем сам сервис
    }

    /**
     * onDestroy - вызывается когда сервис уничтожается
     * Освобождаем все ресурсы чтобы не было утечек памяти
     */
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isActive = false
        mediaPlayer?.release()  // Освобождаем MediaPlayer
        mediaPlayer = null
        vibrator.cancel()       // Останавливаем вибрацию
    }

    /**
     * onBind - нужен только для Bound Services (привязка к Activity)
     * Здесь не используется, возвращаем null
     */
    override fun onBind(intent: Intent?): IBinder? = null
}