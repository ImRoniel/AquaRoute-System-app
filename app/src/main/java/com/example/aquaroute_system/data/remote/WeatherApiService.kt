package com.example.aquaroute_system.data.remote

import com.example.aquaroute_system.BuildConfig
import com.example.aquaroute_system.data.models.WeatherCondition
import com.example.aquaroute_system.data.models.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// OpenWeather API Response Models
data class OpenWeatherResponse(
    val weather: List<WeatherInfo>,
    val main: MainInfo,
    val wind: WindInfo,
    val name: String,
    val dt: Long,
    val sys: SysInfo
)

data class WeatherInfo(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class MainInfo(
    val temp: Double,
    val feels_like: Double,
    val humidity: Int,
    val pressure: Int
)

data class WindInfo(
    val speed: Double,
    val deg: Int,
    val gust: Double?
)

data class SysInfo(
    val country: String,
    val sunrise: Long,
    val sunset: Long
)

// Retrofit Interface
interface OpenWeatherApi {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): OpenWeatherResponse

    @GET("weather")
    suspend fun getWeatherByCity(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): OpenWeatherResponse
}

// Weather Service
object WeatherApiClient {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: OpenWeatherApi = retrofit.create(OpenWeatherApi::class.java)
}

// Weather Repository with OpenWeather
class WeatherApiRepository(
    private val apiKey: String = BuildConfig.OPEN_WEATHER_API_KEY
) {

    suspend fun getWeatherForLocation(lat: Double, lon: Double, locationName: String): Result<WeatherCondition> {
        return try {
            val response = withContext(Dispatchers.IO) {
                WeatherApiClient.api.getCurrentWeather(lat, lon, apiKey)
            }

            val weatherCondition = mapToWeatherCondition(response, locationName)
            Result.Success(weatherCondition)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getWeatherForCity(cityName: String): Result<WeatherCondition> {
        return try {
            val response = withContext(Dispatchers.IO) {
                WeatherApiClient.api.getWeatherByCity(cityName, apiKey)
            }

            val weatherCondition = mapToWeatherCondition(response, response.name)
            Result.Success(weatherCondition)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun mapToWeatherCondition(response: OpenWeatherResponse, locationName: String): WeatherCondition {
        val weatherInfo = response.weather.firstOrNull()
        val icon = when (weatherInfo?.icon) {
            "01d" -> "☀️" // clear sky day
            "01n" -> "🌙" // clear sky night
            "02d", "02n" -> "⛅" // few clouds
            "03d", "03n" -> "☁️" // scattered clouds
            "04d", "04n" -> "☁️" // broken clouds
            "09d", "09n" -> "🌧️" // shower rain
            "10d", "10n" -> "🌦️" // rain
            "11d", "11n" -> "⛈️" // thunderstorm
            "13d", "13n" -> "🌨️" // snow
            "50d", "50n" -> "🌫️" // mist
            else -> "☀️"
        }

        val condition = when (weatherInfo?.main) {
            "Clear" -> "Clear Sky"
            "Clouds" -> "Cloudy"
            "Rain" -> "Rainy"
            "Thunderstorm" -> "Stormy"
            "Snow" -> "Snowy"
            "Mist", "Fog" -> "Foggy"
            else -> weatherInfo?.main ?: "Unknown"
        }

        val windSpeed = "${response.wind.speed} m/s"
        val waveHeight = estimateWaveHeight(response.wind.speed)

        return WeatherCondition(
            location = locationName,
            condition = condition.lowercase(),
            icon = icon,
            temperature = response.main.temp,
            feelsLike = response.main.feels_like,
            humidity = response.main.humidity,
            waves = waveHeight,
            windSpeed = windSpeed,
            windDirection = getWindDirection(response.wind.deg),
            pressure = "${response.main.pressure} hPa",
            hasAdvisory = response.wind.speed > 15 || response.weather.any { it.main == "Thunderstorm" },
            advisoryMessage = when {
                response.wind.speed > 20 -> "High wind warning"
                response.wind.speed > 15 -> "Strong wind advisory"
                response.weather.any { it.main == "Thunderstorm" } -> "Thunderstorm warning"
                else -> null
            },
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun getWindDirection(degrees: Int): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((degrees + 11.5) / 22.5).toInt() % 16
        return directions[index]
    }

    private fun estimateWaveHeight(windSpeed: Double): String {
        // Simple estimation - in reality, wave height depends on fetch, duration, etc.
        return when {
            windSpeed < 3 -> "0.2m"
            windSpeed < 6 -> "0.5m"
            windSpeed < 10 -> "1.0m"
            windSpeed < 14 -> "1.8m"
            windSpeed < 18 -> "2.5m"
            else -> "3.5m+"
        }
    }
}