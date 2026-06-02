package com.example.geoalarm

import java.io.Serializable

data class AlarmData(
    val alarmId: Long = System.currentTimeMillis(),  // Уникальный ID будильника (время создания)
    val hour: Int,                                    // Час срабатывания (0-23)
    val minute: Int,                                  // Минута срабатывания (0-59)
    val latitude: Double,                             // Широта выбранного места
    val longitude: Double,                            // Долгота выбранного места
    val address: String,                              // Адрес или описание места
    val radius: Int = 50,                             // Радиус зоны в метрах (30м)
    val isActive: Boolean = true                      // Активен ли будильник
) : Serializable  // Serializable - чтобы можно было передать через Intent