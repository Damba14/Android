package com.example.login

//noinspection UsingMaterialAndMaterial3Libraries
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    companion object {
        const val REQUEST_CODE_PERMISSIONS = 101
        private const val TAG = "com.example.login.MainActivity"
        private const val UPDATE_INTERVAL = 2000L // 2 секунды
    }

    private lateinit var state: MainActivityState
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = MainActivityState(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MainContent(state)
        }
        checkAndRequestPermissions() // Проверяем и запрашиваем разрешения при создании активности
    }

    private fun checkAndRequestPermissions() {
        val context = applicationContext
        if (!checkPermissions(context)) {
            Log.d(TAG, "Запрос разрешений")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE
                ),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun checkPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
    }

    @Composable
    fun MainContent(state: MainActivityState) {
        val context = LocalContext.current
        var permissionsGranted by remember { mutableStateOf(checkPermissions(context)) }

        LaunchedEffect(context) {
            // Используем context в качестве ключа
            permissionsGranted = checkPermissions(context)
        }

        if (permissionsGranted) {
            LaunchedEffect(Unit) {
                while (true) {
                    getLocation(state)
                    getRsrpValue(state)
                    delay(UPDATE_INTERVAL)
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoginScreen(state)
            }
        } else {
            Text("Ожидание предоставления разрешений...")
        }
    }

    private fun getLocation(state: MainActivityState) {
        Log.d(TAG, "getLocation() called")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Нет разрешения на доступ к местоположению")
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = UPDATE_INTERVAL
            fastestInterval = UPDATE_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    state.latitude = location.latitude.toString()
                    state.longitude = location.longitude.toString()
                    Log.d(TAG, "Location received: Lat=${state.latitude}, Lon=${state.longitude}")
                    state.updateUI() // Обновляем UI после получения новых координат
                }
            }
        }, null)
    }

    private fun getRsrpValue(state: MainActivityState) {
        if (checkPhoneStatePermission(state.context)) {
            val telephonyManager =
                state.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo
            if (cellInfoList.isNullOrEmpty()) {
                state.rsrp = "Список CellInfo пуст"
            } else {
                var rsrp = "Нет данных"
                for (info in cellInfoList) {
                    if (info is CellInfoLte) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val cellSignalStrengthLte = info.cellSignalStrength
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                rsrp = "${cellSignalStrengthLte.rsrp} dBm" // Форматирование строки
                                Log.d(TAG, "RSRP value: $rsrp")
                            }
                        }
                        break
                    }
                }
                state.rsrp = rsrp // Установка нового значения
            }
        } else {
            state.rsrp = "Нет разрешения READ_PHONE_STATE"
        }
    }

    @Composable
    fun LoginScreen(state: MainActivityState) {
        Log.d(TAG, "LoginScreen() called")
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Значение RSRP: ${state.rsrp}"
            )
            Text(
                text = "LAT: ${state.latitude}",
                modifier = Modifier.padding(16.dp)
            )

            Text(
                text = "LON: ${state.longitude}",
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    private fun checkPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

class MainActivityState(val context: Context) {
    var latitude by mutableStateOf("")
    var longitude by mutableStateOf("")
    var rsrp by mutableStateOf("")

    // Обновление UI
    fun updateUI() {
        latitude = latitude // Просто чтение и запись, чтобы обновить состояние и вызвать перерисовку
        longitude = longitude
        rsrp = rsrp
    }
}
