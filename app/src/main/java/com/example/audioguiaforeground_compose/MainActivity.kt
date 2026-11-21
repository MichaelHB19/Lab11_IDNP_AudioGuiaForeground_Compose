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

import android.app.PendingIntent

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

import android.media.MediaPlayer

data class EdificioGuia(
    val id: Int,
    val nombre: String,
    val categoria: String,
    val descripcion: String,
    val audioResId: Int
)

val listaEdificiosCampus = listOf(
    EdificioGuia(
        1, "Ingeniería de Sistemas", "Académico",
        "Aulas y oficinas de la escuela profesional de sistemas",
        R.raw.audio_ingenieria_sistemas
    ),
    EdificioGuia(
        2, "Laboratorios de Sistemas", "Académico",
        "Equipos de cómputo para prácticas",
        R.raw.audio_laboratorios_sistemas
    ),
    EdificioGuia(
        3, "Ciencias de la Computación", "Académico",
        "Aulas y oficinas de la escuela de ciencias",
        R.raw.audio_ciencias_computacion
    ),
    EdificioGuia(
        4, "Biblioteca de Ingenierías", "Servicios",
        "Salas de lectura y préstamo de libros",
        R.raw.audio_biblioteca_ingenierias
    ),
    EdificioGuia(
        5, "Laboratorios de Ciencias", "Académico",
        "Equipos de cómputo para prácticas",
        R.raw.audio_laboratorios_ciencias
    ),
    EdificioGuia(
        6, "Estadio UNSA", "Deportes",
        "Pistas de atletismo y cancha oficial",
        R.raw.audio_estadio_unsa
    ),
    EdificioGuia(
        7, "Cancha de Mecánica", "Deportes",
        "Espacio deportivo al costado de mecánica",
        R.raw.audio_cancha_mecanica
    ),
    EdificioGuia(
        8, "Ingeniería de Minas", "Académico",
        "Aulas y laboratorios de la escuela de minas",
        R.raw.audio_ingenieria_minas
    )
)

@Entity(tableName = "ultimo_edificio")
data class UltimoEdificioEntity(
    @PrimaryKey val id: Int = 1,
    val indiceEdificio: Int,
    val nombreEdificio: String
)

@Dao
interface UltimoEdificioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarUltimo(registro: UltimoEdificioEntity)

    @Query("SELECT * FROM ultimo_edificio WHERE id = 1")
    suspend fun obtenerUltimo(): UltimoEdificioEntity?
}

@Database(entities = [UltimoEdificioEntity::class], version = 1)
abstract class AudioGuiaDatabase : RoomDatabase() {
    abstract fun ultimoEdificioDao(): UltimoEdificioDao
}

fun obtenerBaseDeDatos(context: Context): AudioGuiaDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        AudioGuiaDatabase::class.java,
        "audioguia.db"
    ).build()
}

class AudioGuiaService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var tituloActual: String = listaEdificiosCampus[0].nombre
    private var audioActualResId: Int = listaEdificiosCampus[0].audioResId
    private var estaPausado: Boolean = false

    companion object {
        const val ACCION_REPRODUCIR = "com.example.audioguiaforeground_compose.REPRODUCIR"
        const val ACCION_PAUSAR = "com.example.audioguiaforeground_compose.PAUSAR"
        const val ACCION_DETENER = "com.example.audioguiaforeground_compose.DETENER"
        const val ACCION_CAMBIAR_EDIFICIO = "com.example.audioguiaforeground_compose.CAMBIAR_EDIFICIO"
        const val EXTRA_TITULO_EDIFICIO = "extra_titulo_edificio"
        const val EXTRA_AUDIO_RES_ID = "extra_audio_res_id"
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
        val nuevoAudioResId = intent?.getIntExtra(EXTRA_AUDIO_RES_ID, -1) ?: -1

        when (intent?.action) {
            ACCION_REPRODUCIR -> {
                startForeground(ID_NOTIFICACION, construirNotificacion(tituloActual))
                if (nuevoAudioResId != -1 && nuevoAudioResId != audioActualResId) {
                    audioActualResId = nuevoAudioResId
                    iniciarAudioNuevo(audioActualResId)
                } else if (estaPausado && mediaPlayer != null) {
                    mediaPlayer?.start()
                    estaPausado = false
                } else {
                    if (nuevoAudioResId != -1) audioActualResId = nuevoAudioResId
                    iniciarAudioNuevo(audioActualResId)
                }
            }
            ACCION_CAMBIAR_EDIFICIO -> {
                if (nuevoAudioResId != -1) {
                    audioActualResId = nuevoAudioResId
                    iniciarAudioNuevo(audioActualResId)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(ID_NOTIFICACION, construirNotificacion(tituloActual))
            }
            ACCION_PAUSAR -> {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    estaPausado = true
                }
                val manager = getSystemService(NotificationManager::class.java)
                val notificacionPausada = NotificationCompat.Builder(this, ID_CANAL)
                    .setContentTitle(tituloActual)
                    .setContentText("Audio-guía en pausa")
                    .setSmallIcon(android.R.drawable.ic_media_pause)
                    .setOngoing(true)
                    .addAction(
                        android.R.drawable.ic_delete,
                        "Detener",
                        PendingIntent.getService(
                            this,
                            101,
                            Intent(this, AudioGuiaService::class.java).setAction(ACCION_DETENER),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .build()
                manager.notify(ID_NOTIFICACION, notificacionPausada)
            }
            ACCION_DETENER -> {
                liberarMediaPlayer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun iniciarAudioNuevo(resId: Int) {
        liberarMediaPlayer()
        mediaPlayer = MediaPlayer.create(this, resId)?.apply {
            setOnCompletionListener {
                estaPausado = false
            }
            start()
        }
        estaPausado = false
    }

    private fun liberarMediaPlayer() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        estaPausado = false
    }

    private fun construirNotificacion(titulo: String): Notification {
        val intentPausar = Intent(this, AudioGuiaService::class.java).apply {
            action = ACCION_PAUSAR
        }
        val pendingIntentPausar = PendingIntent.getService(
            this,
            100,
            intentPausar,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intentDetener = Intent(this, AudioGuiaService::class.java).apply {
            action = ACCION_DETENER
        }
        val pendingIntentDetener = PendingIntent.getService(
            this,
            101,
            intentDetener,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ID_CANAL)
            .setContentTitle(titulo)
            .setContentText("Reproduciendo audio-guía")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pausar", pendingIntentPausar)
            .addAction(android.R.drawable.ic_delete, "Detener", pendingIntentDetener)
            .build()
    }

    override fun onDestroy() {
        liberarMediaPlayer()
        super.onDestroy()
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
    val scope = rememberCoroutineScope()
    val dao = remember { obtenerBaseDeDatos(context).ultimoEdificioDao() }

    var reproduciendo by remember { mutableStateOf(false) }
    var enSegundoPlano by remember { mutableStateOf(false) }
    var indiceEdificio by remember { mutableIntStateOf(0) }
    var ultimoGuardado by remember { mutableStateOf<UltimoEdificioEntity?>(null) }

    val edificioActual = listaEdificiosCampus[indiceEdificio]

    LaunchedEffect(Unit) {
        ultimoGuardado = dao.obtenerUltimo()
    }

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
                scope.launch {
                    val registro = UltimoEdificioEntity(
                        indiceEdificio = indiceEdificio,
                        nombreEdificio = edificioActual.nombre
                    )
                    dao.guardarUltimo(registro)
                    ultimoGuardado = registro
                }
                context.startService(
                    Intent(context, AudioGuiaService::class.java)
                        .setAction(AudioGuiaService.ACCION_REPRODUCIR)
                        .putExtra(AudioGuiaService.EXTRA_TITULO_EDIFICIO, edificioActual.nombre)
                        .putExtra(AudioGuiaService.EXTRA_AUDIO_RES_ID, edificioActual.audioResId)
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
                    scope.launch {
                        val registro = UltimoEdificioEntity(
                            indiceEdificio = indiceEdificio,
                            nombreEdificio = siguienteEdificio.nombre
                        )
                        dao.guardarUltimo(registro)
                        ultimoGuardado = registro
                    }
                    context.startService(
                        Intent(context, AudioGuiaService::class.java)
                            .setAction(AudioGuiaService.ACCION_CAMBIAR_EDIFICIO)
                            .putExtra(AudioGuiaService.EXTRA_TITULO_EDIFICIO, siguienteEdificio.nombre)
                            .putExtra(AudioGuiaService.EXTRA_AUDIO_RES_ID, siguienteEdificio.audioResId)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Siguiente edificio")
        }

        ultimoGuardado?.let { guardado ->
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    indiceEdificio = guardado.indiceEdificio
                    val edificioGuardado = listaEdificiosCampus[guardado.indiceEdificio]
                    reproduciendo = true
                    context.startService(
                        Intent(context, AudioGuiaService::class.java)
                            .setAction(AudioGuiaService.ACCION_REPRODUCIR)
                            .putExtra(AudioGuiaService.EXTRA_TITULO_EDIFICIO, edificioGuardado.nombre)
                            .putExtra(AudioGuiaService.EXTRA_AUDIO_RES_ID, edificioGuardado.audioResId)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continuar: ${guardado.nombreEdificio}")
            }
        }
    }
}