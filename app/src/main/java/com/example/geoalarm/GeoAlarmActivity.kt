package com.example.geoalarm

// Импорты для работы с Intent (переключение экранов, остановка сервисов)
import android.content.Intent

// Импорты для рисования иконок (зелёные точки пути)
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

// Импорты для геолокации (GPS)
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Google Play Services для точной геолокации
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// Яндекс.Карты
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider


class GeoAlarmActivity : AppCompatActivity() {

    // ========== UI ЭЛЕМЕНТЫ ==========
    private lateinit var mapView: MapView           // Яндекс.Карта
    private lateinit var tvDistance: TextView       // Текст: расстояние до зоны
    private lateinit var tvStats: TextView          // Текст: статистика (км, скорость)
    private lateinit var tvAddress: TextView        // Текст: адрес цели
    private lateinit var btnEmergency: Button       // Красная кнопка (3 нажатия)

    // ========== ДАННЫЕ ЦЕЛИ ==========
    private var targetPoint: Point? = null          // Координаты цели (от пользователя)
    private var targetAddress: String = ""          // Адрес цели

    // ========== ЭКСТРЕННОЕ ОТКЛЮЧЕНИЕ ==========
    private var clickCount = 0                      // Счётчик нажатий на кнопку
    private val handler = Handler(Looper.getMainLooper())  // Таймер для сброса счётчика

    // ========== GPS ТРЕКИНГ ==========
    private var fusedLocationClient: FusedLocationProviderClient? = null  // Клиент геолокации Google
    private var locationCallback: LocationCallback? = null               // Слушатель изменений GPS
    private var isAlarmStopped = false              // Флаг: будильник уже выключен? (защита от двойного выключения)

    // ========== СТАТИСТИКА ДВИЖЕНИЯ ==========
    private var totalDistance = 0.0                 // Общее пройденное расстояние (метры)
    private var maxSpeed = 0.0                      // Максимальная скорость за поездку (км/ч)
    private var lastLocation: Location? = null      // Предыдущая позиция (для расчёта дистанции)

    // ========== ОТРИСОВКА НА КАРТЕ ==========
    private var startMarker: PlacemarkMapObject? = null      // Синий маркер "ТЫ ЗДЕСЬ"
    private var targetMarker: PlacemarkMapObject? = null     // Красный маркер "ЦЕНТР ЗОНЫ"
    private val pathPoints = mutableListOf<Point>()          // Список всех точек пути
    private val pathCircles = mutableListOf<PlacemarkMapObject>() // Зелёные точки на карте

    // ========== ЛОГИКА ВХОДА/ВЫХОДА ==========
    private var wasInsideZone = false    // Был ли пользователь внутри зоны на ПРОШЛОЙ проверке?
    private var firstCheckDone = false   // Пропущена ли первая проверка? (Да, чтобы не выключилось сразу)
    private val ZONE_RADIUS = 50         // Радиус зоны в метрах (30м - как в AlarmData)

    companion object {
        var isActive = false             // Флаг: активен ли этот экран? (используется в AlarmReceiver)
    }

    /**
     * onCreate - вызывается когда создаётся экран
     * Здесь происходит вся настройка
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_geo_alarm)

        // ===== 1. ИНИЦИАЛИЗАЦИЯ ЯНДЕКС КАРТ =====
        MapKitFactory.initialize(this)

        // ===== 2. ПОЛУЧАЕМ КООРДИНАТЫ ОТ MAINACTIVITY =====
        // Данные пришли через Intent из AlarmReceiver
        targetPoint = Point(
            intent.getDoubleExtra("latitude", 0.0),
            intent.getDoubleExtra("longitude", 0.0)
        )
        targetAddress = intent.getStringExtra("address") ?: "Выбранное место"

        // ===== 3. ПОДКЛЮЧАЕМ UI ЭЛЕМЕНТЫ =====
        mapView = findViewById(R.id.mapview)
        tvDistance = findViewById(R.id.tv_distance)
        tvStats = findViewById(R.id.tv_stats)
        tvAddress = findViewById(R.id.tv_address)
        btnEmergency = findViewById(R.id.btn_emergency)

        // Показываем адрес цели
        tvAddress.text = "🎯 Зона: ${targetAddress.take(50)}"

        // ===== 4. СТАВИМ КРАСНЫЙ МАРКЕР ЦЕЛИ =====
        targetPoint?.let {
            targetMarker = mapView.map.mapObjects.addPlacemark(it)
            targetMarker?.setText("📍 ЦЕНТР ЗОНЫ")  // Текст под маркером
            mapView.map.move(CameraPosition(it, 15f, 0f, 0f))  // Двигаем камеру к цели
        }

        // ===== 5. КРАСНАЯ КНОПКА (3 НАЖАТИЯ ДЛЯ ВЫКЛЮЧЕНИЯ) =====
        btnEmergency.setOnClickListener {
            if (!isAlarmStopped) {
                clickCount++
                when (clickCount) {
                    1 -> Toast.makeText(this, "⚠️ Осталось 2 нажатия", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "⚠️ Осталось 1 нажатие", Toast.LENGTH_SHORT).show()
                    3 -> {
                        Toast.makeText(this, "🔴 БУДИЛЬНИК ВЫКЛЮЧЕН!", Toast.LENGTH_LONG).show()
                        dismissAlarm()  // Выключаем будильник
                    }
                }
                resetClickTimer()  // Запускаем таймер: если 3 секунды без нажатий - сброс счётчика
            }
        }

        // ===== 6. ЗАПУСКАЕМ ОТСЛЕЖИВАНИЕ ГЕОЛОКАЦИИ =====
        startLocationTracking()
        isActive = true

        // ===== 7. ПОДСКАЗКА ПОЛЬЗОВАТЕЛЮ =====
        Toast.makeText(this, "🔔 Будильник выключится при ВХОДЕ или ВЫХОДЕ из зоны (${ZONE_RADIUS}м)", Toast.LENGTH_LONG).show()
    }

    private fun resetClickTimer() {
        handler.removeCallbacksAndMessages(null)           // Удаляем старые таймеры
        handler.postDelayed({ clickCount = 0 }, 3000)     // Через 3 секунды сбросить счётчик
    }

    /**
     * ЗАПУСК ОТСЛЕЖИВАНИЯ ГЕОЛОКАЦИИ
     * Использует FusedLocationProviderClient от Google (самый точный GPS)
     */
    private fun startLocationTracking() {
        // Инициализируем клиент геолокации
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Настройки запроса геолокации:
        // - PRIORITY_HIGH_ACCURACY: использовать GPS, Wi-Fi, Bluetooth (максимальная точность)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        // Создаём слушатель событий изменения геопозиции
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // Берём последнюю известную позицию (самую свежую)
                val location = locationResult.lastLocation

                if (location != null && !isAlarmStopped) {
                    // Обновляем всё:
                    updateStats(location)               // Расчёт пройденного расстояния и скорости
                    updateDistanceToTarget(location)    // Показываем расстояние до зоны
                    updateMapCameraAndPath(location)    // Двигаем камеру, рисуем зелёные точки пути

                    // ===== ГЛАВНАЯ ЛОГИКА: ПРОВЕРКА ВХОДА/ВЫХОДА ИЗ ЗОНЫ =====
                    targetPoint?.let { target ->
                        // Создаём объект Location для цели (чтобы считать расстояние)
                        val targetLoc = Location("target").apply {
                            latitude = target.latitude
                            longitude = target.longitude
                        }

                        // Считаем расстояние от текущей позиции до цели
                        val distance = location.distanceTo(targetLoc)
                        val isInside = distance <= ZONE_RADIUS  // Внутри зоны? (true/false)

                        // ===== ПРОПУСКАЕМ ПЕРВУЮ ПРОВЕРКУ =====
                        // Нужно чтобы будильник НЕ ВЫКЛЮЧИЛСЯ сразу при открытии
                        if (!firstCheckDone) {
                            firstCheckDone = true
                            wasInsideZone = isInside  // Запоминаем где мы были
                            return@let  // Выходим, ничего не выключаем
                        }

                        // ===== ПРОВЕРЯЕМ: ИЗМЕНИЛОСЬ ЛИ СОСТОЯНИЕ? =====
                        if (isInside && !wasInsideZone) {
                            // Были снаружи → стали внутри (ВОШЛИ В ЗОНУ)
                            tvDistance.text = "🎉 ВОШЛИ В ЗОНУ! ВЫКЛЮЧАЮ... 🎉"
                            Toast.makeText(this@GeoAlarmActivity, "✅ Будильник выключен!", Toast.LENGTH_SHORT).show()
                            dismissAlarm()  // ВЫКЛЮЧАЕМ БУДИЛЬНИК

                        } else if (!isInside && wasInsideZone) {
                            // Были внутри → стали снаружи (ВЫШЛИ ИЗ ЗОНЫ)
                            tvDistance.text = "🚪 ВЫШЛИ ИЗ ЗОНЫ! ВЫКЛЮЧАЮ... 🚪"
                            Toast.makeText(this@GeoAlarmActivity, "✅ Будильник выключен!", Toast.LENGTH_SHORT).show()
                            dismissAlarm()  // ВЫКЛЮЧАЕМ БУДИЛЬНИК
                        }

                        // Обновляем состояние (запоминаем где мы сейчас для следующей проверки)
                        wasInsideZone = isInside
                    }
                }
            }
        }

        // Запускаем слушатель, если есть разрешение на геолокацию
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        }
    }

    /**
     * ОБНОВЛЕНИЕ СТАТИСТИКИ
     * Считает пройденное расстояние и максимальную скорость
     *
     * Принцип работы: сравниваем прошлую позицию с текущей
     * - distanceTo() = расстояние между двумя точками
     * - deltaTime = разница во времени
     * - скорость = расстояние / время
     */
    private fun updateStats(currentLocation: Location) {
        if (lastLocation != null) {
            // Расстояние между прошлой и текущей точкой (метры)
            val deltaDistance = currentLocation.distanceTo(lastLocation!!).toDouble()

            // Разница во времени (в секундах)
            val timeDelta = (currentLocation.time - lastLocation!!.time) / 1000.0

            // Скорость в км/ч: (метры / секунды) * 3.6
            val speed = if (timeDelta > 0) (deltaDistance / timeDelta) * 3.6 else 0.0

            // ФИЛЬТР ШУМА GPS:
            // Игнорируем выбросы:
            // - расстояние меньше 1 метра (шум стояния на месте)
            // - расстояние больше 50 метров (выброс GPS)
            // - скорость больше 30 км/ч (пешком или велосипед)
            // - скорость меньше 0.5 км/ч (шум)
            if (deltaDistance > 1.0 && deltaDistance < 50.0 && speed < 30.0 && speed > 0.5) {
                totalDistance += deltaDistance
                if (speed > maxSpeed) maxSpeed = speed
            }
        }
        lastLocation = currentLocation  // Запоминаем текущую позицию для следующего расчёта

        // Обновляем текст на экране
        val distanceKm = totalDistance / 1000.0
        tvStats.text = String.format(
            "📊 Пройдено: %.2f км | 📈 Макс: %.1f км/ч",
            distanceKm, maxSpeed
        )
    }

    /**
     * ОБНОВЛЕНИЕ КАРТЫ: маркер пользователя + зелёные точки пути
     */
    private fun updateMapCameraAndPath(currentLocation: Location) {
        val userPoint = Point(currentLocation.latitude, currentLocation.longitude)

        // ===== 1. ОБНОВЛЯЕМ СИНИЙ МАРКЕР "ТЫ ЗДЕСЬ" =====
        startMarker?.let { mapView.map.mapObjects.remove(it) }  // Удаляем старый
        startMarker = mapView.map.mapObjects.addPlacemark(userPoint)  // Создаём новый
        startMarker?.setText("🚩 ТЫ ЗДЕСЬ")

        // ===== 2. ДОБАВЛЯЕМ ЗЕЛЁНУЮ ТОЧКУ ПУТИ =====
        if (pathPoints.isEmpty()) {
            // Первая точка - просто добавляем
            pathPoints.add(userPoint)
            addPathDot(userPoint)
        } else {
            // Проверяем расстояние от последней точки
            val lastPoint = pathPoints.last()
            val lastLoc = Location("").apply {
                latitude = lastPoint.latitude
                longitude = lastPoint.longitude
            }
            val currentLoc = Location("").apply {
                latitude = userPoint.latitude
                longitude = userPoint.longitude
            }
            val distance = lastLoc.distanceTo(currentLoc)

            // Добавляем точку только если отошли на 5+ метров (экономия ресурсов)
            if (distance > 5) {
                pathPoints.add(userPoint)
                addPathDot(userPoint)
            }
        }

        // ===== 3. ДВИГАЕМ КАМЕРУ ЗА ПОЛЬЗОВАТЕЛЕМ =====
        mapView.map.move(CameraPosition(userPoint, 16f, 0f, 0f))
    }

    /**
     * ДОБАВЛЯЕТ ЗЕЛЁНУЮ ТОЧКУ НА КАРТУ
     * Точки создают видимость непрерывной линии пути
     */
    private fun addPathDot(point: Point) {
        val dot = mapView.map.mapObjects.addPlacemark(point)  // Создаём маркер
        dot.setIcon(createGreenDotIcon())  // Делаем его зелёным кружком
        pathCircles.add(dot)  // Сохраняем в список (на случай если понадобится удалить)
    }

    /**
     * СОЗДАЁТ ЗЕЛЁНУЮ ИКОНКУ-КРУЖОК ДЛЯ МАРКЕРА
     * Используется вместо стандартной красной точки
     */
    private fun createGreenDotIcon(): ImageProvider {
        // Создаём пустое изображение 12x12 пикселей
        val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Настраиваем кисть: зелёный цвет, заливка
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.FILL
        }

        // Рисуем круг: центр (6,6), радиус 5
        canvas.drawCircle(6f, 6f, 5f, paint)

        // Превращаем картинку в иконку для Яндекс.Карт
        return ImageProvider.fromBitmap(bitmap)
    }

    /**
     * ПОКАЗЫВАЕТ РАССТОЯНИЕ ДО ЦЕЛИ
     * Просто текст: внутри зоны или снаружи
     */
    private fun updateDistanceToTarget(currentLocation: Location) {
        targetPoint?.let { target ->
            val targetLoc = Location("target").apply {
                latitude = target.latitude
                longitude = target.longitude
            }
            val distance = currentLocation.distanceTo(targetLoc)

            val distanceText = when {
                distance <= ZONE_RADIUS -> "📍 ВНУТРИ ЗОНЫ (${String.format("%.0f", distance)} м)"
                else -> "🚪 СНАРУЖИ ЗОНЫ (${String.format("%.0f", distance)} м)"
            }
            tvDistance.text = distanceText
        }
    }

    private fun dismissAlarm() {
        if (isAlarmStopped) return
        isAlarmStopped = true

        // Останавливаем звук будильника
        stopService(Intent(this, AlarmSoundService::class.java))

        // GeoAlarmService не нужен - GeoAlarmActivity сам отслеживает GPS

        isActive = false
        finish()  // Закрываем экран
    }
    /**
     * ЖИЗНЕННЫЙ ЦИКЛ ЯНДЕКС КАРТ
     * Обязательно вызывать onStart/onStop для корректной работы
     */
    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    /**
     * ОСВОБОЖДАЕМ РЕСУРСЫ ПРИ ЗАКРЫТИИ
     * Отключаем слушатель геолокации, чтобы не тратить батарею
     */
    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
    }

    /**
     * БЛОКИРУЕМ КНОПКУ "НАЗАД"
     * Чтобы пользователь не мог закрыть будильник просто нажатием "Назад"
     */
    override fun onBackPressed() {
        Toast.makeText(this, "Будильник выключится при ВХОДЕ или ВЫХОДЕ из зоны", Toast.LENGTH_SHORT).show()
    }
}