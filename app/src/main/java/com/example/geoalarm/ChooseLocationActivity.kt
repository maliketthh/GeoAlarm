package com.example.geoalarm

// Импорты для разрешений и геолокации
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Импорты для работы с Intent (передача данных между экранами)
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Google Play Services для точной геолокации
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// Яндекс.Карты
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView

// Для форматирования координат и поиска адресов
import java.util.Locale



class ChooseLocationActivity : AppCompatActivity() {

    // ========== UI ЭЛЕМЕНТЫ ==========
    private lateinit var mapView: MapView              // Яндекс.Карта
    private lateinit var etLocationName: EditText      // Поле ввода адреса
    private lateinit var btnSearch: Button             // Кнопка "Найти"
    private lateinit var btnConfirm: Button            // Кнопка "Подтвердить"
    private lateinit var btnMyLocation: Button         // Кнопка "Моё местоположение"

    // ========== ДАННЫЕ ВЫБРАННОЙ ТОЧКИ ==========
    private var selectedPoint: Point? = null           // Координаты выбранной точки
    private var placemark: PlacemarkMapObject? = null  // Маркер на карте (красная точка)

    // ========== ГЕОЛОКАЦИЯ ==========
    private var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    // FusedLocationProviderClient - Google сервис для получения точной геопозиции

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 100  // Код запроса разрешения
    }

    /**
     * onCreate - вызывается когда создаётся экран
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_location)

        // ===== 1. ПОДКЛЮЧАЕМ UI ЭЛЕМЕНТЫ =====
        mapView = findViewById(R.id.mapview)
        etLocationName = findViewById(R.id.et_location_name)
        btnSearch = findViewById(R.id.btn_search)
        btnConfirm = findViewById(R.id.btn_confirm)
        btnMyLocation = findViewById(R.id.btn_my_location)

        // ===== 2. НАЧАЛЬНАЯ ПОЗИЦИЯ КАМЕРЫ =====
        // Если геолокация не работает - показываем Москву (Кремль)
        val startPoint = Point(55.751244, 37.618423)
        mapView.map.move(CameraPosition(startPoint, 12.0f, 0.0f, 0.0f))
        // 12.0f - масштаб (чем больше, тем ближе к земле)

        // ===== 3. ИНИЦИАЛИЗИРУЕМ GOOGLE LOCATION SERVICES =====
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // ===== 4. КНОПКА "МОЁ МЕСТОПОЛОЖЕНИЕ" =====
        btnMyLocation.setOnClickListener {
            checkLocationPermissionAndMoveToMyLocation()  // Проверяем разрешение и двигаем камеру
        }

        // ===== 5. КЛИК ПО КАРТЕ =====
        // Пользователь нажимает на карту → выбирает точку
        mapView.map.addTapListener { event ->
            // Получаем центр камеры как выбранную точку
            val point = mapView.map.cameraPosition.target
            selectedPoint = point
            addMarker(point)  // Ставим красный маркер

            // Показываем координаты в поле ввода
            etLocationName.setText(String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude))
            Toast.makeText(this, "📍 Точка выбрана", Toast.LENGTH_SHORT).show()
            true  // Возвращаем true - событие обработано
        }

        // ===== 6. ПОИСК ПО АДРЕСУ =====
        btnSearch.setOnClickListener {
            val query = etLocationName.text.toString().trim()
            if (query.isNotEmpty()) {
                searchLocation(query)  // Ищем адрес через Geocoder
            } else {
                Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show()
            }
        }

        // ===== 7. ПОДТВЕРЖДЕНИЕ ВЫБОРА =====
        btnConfirm.setOnClickListener {
            if (selectedPoint != null) {
                // Создаём Intent с результатом
                val resultIntent = Intent().apply {
                    putExtra("latitude", selectedPoint!!.latitude)   // Широта
                    putExtra("longitude", selectedPoint!!.longitude) // Долгота
                    putExtra("address", etLocationName.text.toString()) // Адрес/описание
                }
                setResult(RESULT_OK, resultIntent)  // Отправляем результат обратно в MainActivity
                finish()  // Закрываем этот экран
            } else {
                Toast.makeText(this, "Нажмите на карту или нажмите кнопку 'Моё место'", Toast.LENGTH_LONG).show()
            }
        }

        // ===== 8. АВТОМАТИЧЕСКИ ПОКАЗЫВАЕМ МЕСТОПОЛОЖЕНИЕ =====
        // При открытии экрана пытаемся показать где пользователь
        checkLocationPermissionAndMoveToMyLocation()
    }

    /**
     * Проверяет разрешение на геолокацию
     * - Если есть → вызывает getMyLocationAndMoveCamera()
     * - Если нет → запрашивает у пользователя
     */
    private fun checkLocationPermissionAndMoveToMyLocation() {
        // Проверяем, дал ли пользователь разрешение на точную геолокацию
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Разрешения нет - запрашиваем
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        } else {
            // Разрешение есть - получаем местоположение
            getMyLocationAndMoveCamera()
        }
    }

    /**
     * Получает текущее местоположение пользователя (GPS)
     * и двигает камеру на эту точку
     */
    private fun getMyLocationAndMoveCamera() {
        // Ещё раз проверяем разрешение (для безопасности)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // lastLocation - последняя известная позиция (может быть null если GPS ещё не включился)
        fusedLocationClient?.lastLocation?.addOnSuccessListener { location: Location? ->
            if (location != null) {
                // Создаём точку из координат
                val myPoint = Point(location.latitude, location.longitude)

                // Двигаем камеру на моё местоположение (масштаб 15 - достаточно близко)
                mapView.map.move(CameraPosition(myPoint, 15.0f, 0.0f, 0.0f))

                // Ставим маркер
                addMarker(myPoint)
                selectedPoint = myPoint

                // Показываем координаты в поле ввода
                etLocationName.setText(String.format(Locale.US, "Моё местоположение\n%.5f, %.5f", location.latitude, location.longitude))
                Toast.makeText(this, "📍 Вы здесь", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Не удалось определить местоположение. Включите GPS.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Callback - вызывается после того как пользователь ответил на запрос разрешения
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Пользователь дал разрешение - получаем геопозицию
                getMyLocationAndMoveCamera()
            } else {
                // Пользователь отказал
                Toast.makeText(this, "Нужно разрешение для определения вашего местоположения", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * ПОИСК МЕСТА ПО АДРЕСУ
     * Использует Geocoder - превращает текст ("Москва") в координаты (55.75, 37.61)
     */
    private fun searchLocation(query: String) {
        // Geocoder - системный сервис Android для работы с адресами
        // Locale("ru", "RU") - искать на русском языке
        val geocoder = android.location.Geocoder(this, Locale("ru", "RU"))

        try {
            // getFromLocationName - ищет адрес по тексту (5 - максимум результатов)
            val addresses = geocoder.getFromLocationName(query, 5)

            if (!addresses.isNullOrEmpty()) {
                // Берём первый найденный результат
                val location = addresses[0]
                val point = Point(location.latitude, location.longitude)

                selectedPoint = point
                addMarker(point)  // Ставим маркер

                // Показываем полный адрес (улица, город, страна)
                val fullAddress = location.getAddressLine(0) ?: query
                etLocationName.setText(fullAddress)

                Toast.makeText(this, "✅ Найдено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Не найдено", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ДОБАВЛЯЕТ МАРКЕР НА КАРТУ
     * Удаляет старый маркер и ставит новый в указанной точке
     */
    private fun addMarker(point: Point) {
        // Удаляем старый маркер, если есть
        placemark?.let { mapView.map.mapObjects.remove(it) }
        // Создаём новый
        placemark = mapView.map.mapObjects.addPlacemark(point)
        // Двигаем камеру к маркеру (масштаб 15)
        mapView.map.move(CameraPosition(point, 15.0f, 0.0f, 0.0f))
    }

    /**
     * ЖИЗНЕННЫЙ ЦИКЛ ЯНДЕКС КАРТ
     * Обязательно вызывать onStart/onStop для корректной работы
     */
    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()  // Запускаем карты
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()   // Останавливаем карты
        super.onStop()
    }
}