package com.example.geoalarm

// Импорт для Application (базовый класс приложения)
import android.app.Application

// Импорт для Яндекс.Карт (инициализация библиотеки)
import com.yandex.mapkit.MapKitFactory

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()  // Обязательно вызываем родительский конструктор
        MapKitFactory.setApiKey("acfd85d6-7d2a-4d74-8c9e-9ed8edbe36e4")

        // Загружаем все необходимые ресурсы для работы карт
        MapKitFactory.initialize(this)


    }
}