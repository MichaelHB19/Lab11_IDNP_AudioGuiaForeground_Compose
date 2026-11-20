package com.example.audioguiaforeground_compose

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.audioguiaforeground_compose.ui.theme.AudioGuiaForeground_ComposeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class EdificioGuia(
    val id: Int,
    val nombre: String,
    val categoria: String,
    val descripcion: String
)

val listaEdificiosCampus = listOf(
    EdificioGuia(1, "Ingeniería de Sistemas", "Académico", "Aulas y oficinas de la escuela profesional de sistemas"),
    EdificioGuia(2, "Laboratorios de Sistemas", "Académico", "Equipos de cómputo para prácticas"),
    EdificioGuia(3, "Ciencias de la Computación", "Académico", "Aulas y oficinas de la escuela de ciencias"),
    EdificioGuia(4, "Biblioteca de Ingenierías", "Servicios", "Salas de lectura y préstamo de libros"),
    EdificioGuia(5, "Laboratorios de Ciencias", "Académico", "Equipos de cómputo para prácticas"),
    EdificioGuia(6, "Estadio UNSA", "Deportes", "Pistas de atletismo y cancha oficial"),
    EdificioGuia(7, "Cancha de Mecánica", "Deportes", "Espacio deportivo al costado de mecánica"),
    EdificioGuia(8, "Ingeniería de Minas", "Académico", "Aulas y laboratorios de la escuela de minas")
)

class AudioGuiaService : Service() {

    private var job: Job? = null
    private var tituloActual: String = listaEdificiosCampus[0].nombre

    companion object {
        const val ACCION_REPRODUCIR = "com.example.audioguiaforeground_compose.REPRODUCIR"
        const val ACCION_PAUSAR = "com.example.audioguiaforeground_compose.PAUSAR"
        const val ACCION_DETENER = "com.example.audioguiaforeground_compose.DETENER"
        const val ACCION_CAMBIAR_EDIFICIO = "com.example.audioguiaforeground_compose.CAMBIAR_EDIFICIO"
        const val EXTRA_TITULO_EDIFICIO = "extra_titulo_edificio"
        private const val ID_CANAL = "canal_audioguia"
        private const val ID_NOTIFICACION = 1
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                ID_CANAL,
                "Audio-guía",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nuevoTitulo = intent?.getStringExtra(EXTRA_TITULO_EDIFICIO)
        if (!nuevoTitulo.isNullOrBlank()) {
            tituloActual = nuevoTitulo
        }

        when (intent?.action) {
            ACCION_REPRODUCIR -> {
                startForeground(ID_NOTIFICACION, construirNotificacion(tituloActual))
                iniciarAvance()
            }
            ACCION_CAMBIAR_EDIFICIO -> {
                // Actualiza el título en la notificación persistente
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(ID_NOTIFICACION, construirNotificacion(tituloActual))
            }
            ACCION_PAUSAR -> job?.cancel()
            ACCION_DETENER -> {
                job?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun iniciarAvance() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            var segundos = 0
            while (true) {
                delay(1000)
                segundos++
            }
        }
    }

    private fun construirNotificacion(titulo: String): Notification {
        return NotificationCompat.Builder(this, ID_CANAL)
            .setContentTitle(titulo)
            .setContentText("Reproduciendo audio-guía")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioGuiaForeground_ComposeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PantallaAudioGuia()
                }
            }
        }
    }
}

@Composable
fun PantallaAudioGuia() {
    val context = LocalContext.current
    var reproduciendo by remember { mutableStateOf(false) }
    var enSegundoPlano by remember { mutableStateOf(false) }
    var indiceEdificio by remember { mutableIntStateOf(0) }

    val edificioActual = listaEdificiosCampus[indiceEdificio]

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> enSegundoPlano = true
                Lifecycle.Event.ON_START -> enSegundoPlano = false
                else -> {}
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Edificio ${indiceEdificio + 1} de ${listaEdificiosCampus.size}: ${edificioActual.nombre}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Categoría: ${edificioActual.categoria}", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = edificioActual.descripcion, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(if (reproduciendo) "Reproduciendo audio-guía..." else "Audio-guía detenida")
        if (enSegundoPlano && reproduciendo) {
            Text("La app está en segundo plano: revisa la notificación")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = {
                reproduciendo = true
                context.startService(
                    Intent(context, AudioGuiaService::class.java)
                        .setAction(AudioGuiaService.ACCION_REPRODUCIR)
                        .putExtra(AudioGuiaService.EXTRA_TITULO_EDIFICIO, edificioActual.nombre)
                )
            }) {
                Text("Reproducir")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                reproduciendo = false
                context.startService(
                    Intent(context, AudioGuiaService::class.java)
                        .setAction(AudioGuiaService.ACCION_PAUSAR)
                )
            }) {
                Text("Pausar")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                reproduciendo = false
                context.startService(
                    Intent(context, AudioGuiaService::class.java)
                        .setAction(AudioGuiaService.ACCION_DETENER)
                )
            }) {
                Text("Detener")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                indiceEdificio = (indiceEdificio + 1) % listaEdificiosCampus.size
                val siguienteEdificio = listaEdificiosCampus[indiceEdificio]
                if (reproduciendo) {
                    context.startService(
                        Intent(context, AudioGuiaService::class.java)
                            .setAction(AudioGuiaService.ACCION_CAMBIAR_EDIFICIO)
                            .putExtra(AudioGuiaService.EXTRA_TITULO_EDIFICIO, siguienteEdificio.nombre)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Siguiente edificio")
        }
    }
}