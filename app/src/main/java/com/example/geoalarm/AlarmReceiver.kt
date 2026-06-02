package com.example.geoalarm

// Импорты для уведомлений
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent

// Импорты для BroadcastReceiver (приёмник системных событий)
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Импорты для работы с версиями Android
import android.os.Build
import android.os.PowerManager

// Импорт для создания уведомлений
import androidx.core.app.NotificationCompat


class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        // PowerManager - сервис для управления питанием
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // WakeLock - блокировка сна. Удерживает процессор активным
        // PARTIAL_WAKE_LOW - держит CPU включённым
        // ACQUIRE_CAUSES_WAKEUP - включает экран
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AlarmReceiver:WakeLock"  // Тег для отладки
        )

        // Захватываем блокировку на 60 секунд (чтобы телефон не уснул пока звенит будильник)
        wakeLock.acquire(60 * 1000L)

        // ===== 2. ПОЛУЧАЕМ ДАННЫЕ О БУДИЛЬНИКЕ =====
        // intent.getSerializableExtra - достаём объект AlarmData который передали в MainActivity
        val alarmData = intent.getSerializableExtra("alarm_data") as? AlarmData

        // ===== 3. ЗАПУСКАЕМ ЭКРАН С КАРТОЙ И ЗВУК =====
        if (alarmData != null) {

            // ПРОВЕРКА: если GeoAlarmActivity уже открыта - не создаём новую
            // GeoAlarmActivity.isActive - статический флаг из GeoAlarmActivity
            if (!GeoAlarmActivity.isActive) {

                // Создаём Intent для открытия GeoAlarmActivity (экран с картой)
                val geoAlarmIntent = Intent(context, GeoAlarmActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)      // Открыть в новой задаче
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)   // Не создавать дубликат
                    putExtra("latitude", alarmData.latitude)     // Передаём широту цели
                    putExtra("longitude", alarmData.longitude)   // Передаём долготу цели
                    putExtra("address", alarmData.address)       // Передаём адрес цели
                }
                // Запускаем Activity (экран)
                context.startActivity(geoAlarmIntent)
            }

            // ЗАПУСКАЕМ ЗВУК БУДИЛЬНИКА (если ещё не играет)
            // AlarmSoundService.isActive - флаг из сервиса звука
            if (!AlarmSoundService.isActive) {
                val soundIntent = Intent(context, AlarmSoundService::class.java)

                // Для Android 8+ нужен startForegroundService (сервис с уведомлением)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(soundIntent)
                } else {
                    context.startService(soundIntent)  // Старые версии
                }
            }
        }

        // ===== 4. ПОКАЗЫВАЕМ УВЕДОМЛЕНИЕ В ШТОРКЕ =====
        showNotification(context, alarmData)
    }

    /**
     * Показывает уведомление в статус-баре
     * @param context - контекст
     * @param alarmData - данные будильника (чтобы показать куда ехать)
     */
    private fun showNotification(context: Context, alarmData: AlarmData?) {
        val channelId = "alarm_channel"  // ID канала уведомлений
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ===== 1. СОЗДАЁМ КАНАЛ УВЕДОМЛЕНИЯ (нужно для Android 8+) =====
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Будильник",
                NotificationManager.IMPORTANCE_HIGH  // Высокий приоритет (звук, всплывающее окно)
            )
            notificationManager.createNotificationChannel(channel)
        }

        // ===== 2. СОЗДАЁМ Intent ДЛЯ ОТКРЫТИЯ MainActivity =====
        // Когда пользователь нажмёт на уведомление - откроется главный экран
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // PendingIntent - "отложенный" Intent, который выполнится при нажатии на уведомление
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE  // Флаг для безопасности (Android 12+)
            else 0
        )

        // ===== 3. СОЗДАЁМ САМО УВЕДОМЛЕНИЕ =====
        val address = alarmData?.address ?: "выбранного места"  // Если адреса нет - пишем так

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("⏰ ГЕО-БУДИЛЬНИК!")              // Заголовок
            .setContentText("Езжай в: $address")               // Текст
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Иконка (будильник)
            .setContentIntent(pendingIntent)                    // Что делать при нажатии
            .setPriority(NotificationCompat.PRIORITY_MAX)       // Максимальный приоритет
            .setAutoCancel(true)                                // Удалить после нажатия
            .build()

        // ===== 4. ПОКАЗЫВАЕМ УВЕДОМЛЕНИЕ =====
        // ID 1001 - уникальный идентификатор (чтобы можно было обновить или отменить)
        notificationManager.notify(1001, notification)
    }
}