package com.example.geoalarm

// Импорты для работы с будильником (AlarmManager)
import android.app.AlarmManager
import android.app.PendingIntent

// Импорты для контекста и интентов (переключение между экранами)
import android.content.Context
import android.content.Intent

// Импорты для работы с версиями Android
import android.os.Build

// Импорты для проверки разрешений
import android.content.pm.PackageManager

// Базовые импорты Android
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast

// AppCompatActivity - базовый класс для экранов с поддержкой современных фич
import androidx.appcompat.app.AppCompatActivity

// Импорты для работы с датой и временем
import java.util.*

/**
 * MainActivity - ГЛАВНЫЙ ЭКРАН ПРИЛОЖЕНИЯ
 *
 * ЧТО ДЕЛАЕТ:
 * 1. Показывает текущее время (обновляется каждую секунду)
 * 2. Позволяет выбрать время будильника (TimePicker)
 * 3. Позволяет выбрать место на карте (кнопка "Выбрать место")
 * 4. Устанавливает будильник в системе (AlarmManager)
 *
 * КАК РАБОТАЕТ:
 * - Пользователь выбирает время → нажимает "Установить будильник"
 * - Выбранные координаты и время сохраняются в AlarmData
 * - AlarmManager в назначенное время запускает AlarmReceiver
 * - AlarmReceiver открывает GeoAlarmActivity с картой
 */
class MainActivity : AppCompatActivity() {

    // ========== UI ЭЛЕМЕНТЫ ==========
    private lateinit var alarmManager: AlarmManager   // Системный сервис будильников
    private lateinit var timePicker: TimePicker       // Виджет выбора времени (часы/минуты)
    private lateinit var tvStatus: TextView           // Текст статуса (информация о будильнике)
    private lateinit var tvCurrentTime: TextView      // Текст с текущим временем (обновляется)

    // ========== ДАННЫЕ ВЫБРАННОГО МЕСТА ==========
    private var selectedLatitude: Double = 55.751244     // Широта (по умолчанию Москва, Кремль)
    private var selectedLongitude: Double = 37.618423    // Долгота
    private var selectedAddress: String = "Москва, Кремль"  // Адрес

    companion object {
        private const val REQUEST_LOCATION = 1001  // Код запроса разрешений (уникальный)
    }

    /**
     * onCreate - вызывается когда создаётся экран
     * Здесь происходит вся начальная настройка
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ===== 1. ПОДКЛЮЧАЕМ UI ЭЛЕМЕНТЫ =====
        timePicker = findViewById(R.id.time_picker)
        tvStatus = findViewById(R.id.tv_status)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        val btnSetAlarm = findViewById<Button>(R.id.btn_set_alarm)
        val btnChooseLocation = findViewById<Button>(R.id.btn_choose_location)

        // ===== 2. ПОЛУЧАЕМ СИСТЕМНЫЙ СЕРВИС БУДИЛЬНИКОВ =====
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // ===== 3. ЗАПУСКАЕМ ОБНОВЛЕНИЕ ТЕКУЩЕГО ВРЕМЕНИ =====
        updateTimeDisplay()

        // ===== 4. ПРОВЕРЯЕМ И ЗАПРАШИВАЕМ РАЗРЕШЕНИЯ =====
        // PermissionsHelper - утилита для работы с разрешениями
        if (!PermissionsHelper.hasAllPermissions(this)) {
            PermissionsHelper.requestPermissions(this, REQUEST_LOCATION)
        }

        // ===== 5. КНОПКА "ВЫБРАТЬ МЕСТО НА КАРТЕ" =====
        btnChooseLocation.setOnClickListener {
            // Запускаем ChooseLocationActivity (экран с картой)
            val intent = Intent(this, ChooseLocationActivity::class.java)
            startActivityForResult(intent, 1)  // 1 - код запроса (чтобы потом обработать результат)
        }

        // ===== 6. КНОПКА "УСТАНОВИТЬ БУДИЛЬНИК" =====
        btnSetAlarm.setOnClickListener {
            setAlarm()  // Устанавливаем будильник в системе
        }
    }

    /**
     * onActivityResult - вызывается когда дочернее Activity (ChooseLocationActivity) закрывается
     * Получаем выбранные пользователем координаты и адрес
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Проверяем: это результат от ChooseLocationActivity? (requestCode == 1)
        // И пользователь подтвердил выбор? (resultCode == RESULT_OK)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Получаем координаты и адрес из Intent
            selectedLatitude = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            selectedLongitude = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
            selectedAddress = data?.getStringExtra("address") ?: "Выбранное место"

            // Обновляем статус на экране (показываем выбранное место)
            tvStatus.text = "📍 Место: ${selectedAddress.take(30)}..."
            Toast.makeText(this, "Место выбрано: $selectedAddress", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * updateTimeDisplay - показывает текущее время в реальном времени
     * Использует Handler для обновления каждую секунду
     */
    private fun updateTimeDisplay() {
        // Handler для выполнения задач в главном потоке (UI)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        // Создаём задачу, которая будет выполняться каждую секунду
        val runnable = object : Runnable {
            override fun run() {
                // Получаем текущее время
                val calendar = Calendar.getInstance()
                val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                // Обновляем TextView
                tvCurrentTime.text = timeFormat.format(calendar.time)

                // Запускаем задачу снова через 1000 миллисекунд (1 секунду)
                handler.postDelayed(this, 1000)
            }
        }

        // Запускаем задачу в первый раз
        handler.post(runnable)
    }

    /**
     * setAlarm - УСТАНОВКА БУДИЛЬНИКА
     * Главный метод, который регистрирует будильник в системе
     */
    private fun setAlarm() {
        // ===== 1. ПОЛУЧАЕМ ВРЕМЯ ИЗ TIME PICKER =====
        val calendar = Calendar.getInstance()

        // Получаем час (в зависимости от версии Android)
        val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.hour                    // Android 6+ (новый API)
        } else {
            timePicker.currentHour             // Старые версии
        }

        // Получаем минуты
        val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.minute
        } else {
            timePicker.currentMinute
        }

        // Устанавливаем выбранное время в календарь
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)  // Секунды = 0 (ровно в эту минуту)

        // ===== 2. ПРОВЕРЯЕМ: ВРЕМЯ УЖЕ ПРОШЛО? =====
        var alarmTime = calendar.timeInMillis  // Время будильника в миллисекундах

        // Если выбранное время уже прошло сегодня → добавляем 24 часа (на завтра)
        if (alarmTime < System.currentTimeMillis()) {
            alarmTime += 24 * 60 * 60 * 1000  // 24 часа в миллисекундах
        }

        // ===== 3. СОЗДАЁМ ОБЪЕКТ С ДАННЫМИ БУДИЛЬНИКА =====
        val alarmData = AlarmData(
            hour = hour,
            minute = minute,
            latitude = selectedLatitude,
            longitude = selectedLongitude,
            address = selectedAddress,
            radius = 200  // Радиус срабатывания 200 метров (будет переопределён в AlarmData)
        )

        // ===== 4. СОЗДАЁМ INTENT ДЛЯ ALARMRECEIVER =====
        // Intent говорит системе: "в нужное время запусти AlarmReceiver"
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("alarm_data", alarmData)  // Передаём данные будильника
        }

        // ===== 5. СОЗДАЁМ PENDINGINTENT =====
        // PendingIntent - "обёртка" для Intent, которая сработает в будущем
        // getBroadcast - потому что AlarmReceiver это BroadcastReceiver
        val pendingIntent = PendingIntent.getBroadcast(
            this,                                    // Контекст
            alarmData.alarmId.toInt(),               // Уникальный ID (чтобы не конфликтовали)
            intent,                                  // Intent для запуска
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE         // Флаг для Android 12+ (безопасность)
            else 0
        )

        // ===== 6. РЕГИСТРИРУЕМ БУДИЛЬНИК В СИСТЕМЕ =====
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Для Android 6+ используем setAlarmClock (самый надёжный способ)
            // Система покажет иконку часов в статус-баре
            val alarmInfo = AlarmManager.AlarmClockInfo(alarmTime, pendingIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
        } else {
            // Для старых версий используем setExact (точный будильник)
            // RTC_WAKEUP - будить телефон в указанное время
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
        }

        // ===== 7. ПОКАЗЫВАЕМ ПОДТВЕРЖДЕНИЕ ПОЛЬЗОВАТЕЛЮ =====
        val dateFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        tvStatus.text = "⏰ Будильник на ${dateFormat.format(Date(alarmTime))}\n📍 $selectedAddress"
        Toast.makeText(this, "Гео-будильник установлен!", Toast.LENGTH_LONG).show()
    }

    /**
     * onRequestPermissionsResult - вызывается после ответа пользователя на запрос разрешений
     * @param requestCode - код запроса (проверяем что это наш запрос)
     * @param grantResults - результаты (дал/не дал разрешение)
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Проверяем: это наш запрос на геолокацию? (REQUEST_LOCATION = 1001)
        if (requestCode == REQUEST_LOCATION) {
            // Проверяем: все ли разрешения даны? (all { it == PackageManager.PERMISSION_GRANTED })
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Нужны разрешения для геолокации", Toast.LENGTH_LONG).show()
            }
        }
    }
}