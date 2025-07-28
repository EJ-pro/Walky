// WeatherRepository.kt
package com.example.walky.data

import com.google.gson.Gson
import okhttp3.*
import okhttp3.HttpUrl
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// JSON 파싱용 데이터 클래스 (필요한 필드만 정의)
data class WeatherResponse(
    val name: String,
    val main: Main,
    val weather: List<Weather>
) {
    data class Main(val temp: Double)
    data class Weather(val description: String, val icon: String)
}

class WeatherRepository {
    private val client = OkHttpClient()
    private val apiKey = "ef20c8c5602c607d08060a5a277e9c28"

    /**
     * 위도(lat), 경도(lon)로 현재 날씨를 조회합니다.
     * 성공하면 WeatherResponse, 실패하면 예외를 던집니다.
     */
    suspend fun fetchCurrentWeatherByCoords(lat: Double, lon: Double): WeatherResponse =
        suspendCancellableCoroutine { cont ->
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.openweathermap.org")
                .addPathSegments("data/2.5/weather")
                .addQueryParameter("lat", lat.toString())
                .addQueryParameter("lon", lon.toString())
                .addQueryParameter("units", "metric")       // 섭씨 온도
                .addQueryParameter("appid", apiKey)
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            cont.resumeWithException(IOException("HTTP ${resp.code}"))
                            return
                        }
                        val body = resp.body?.string()
                        if (body == null) {
                            cont.resumeWithException(IOException("Empty body"))
                        } else {
                            try {
                                val weather = Gson().fromJson(body, WeatherResponse::class.java)
                                cont.resume(weather)
                            } catch (e: Exception) {
                                cont.resumeWithException(e)
                            }
                        }
                    }
                }
            })
        }
}
