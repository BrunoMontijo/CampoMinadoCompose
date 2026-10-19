package com.example.campominadokotlin

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                CampoMinadoApp(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

class CelulaEstado(val linha: Int, val coluna: Int) {
    var valor by mutableStateOf(0)
    var revelada by mutableStateOf(false)
    var marcacao by mutableStateOf(0)
}

data class PartidaRecorde(
    val nomeJogador: String,
    val jogadas: Int,
    val tempoSegundos: Int,
    val dataHora: String,
    val nivel: String
)

data class DificuldadeConfig(
    val nome: String,
    val linhas: Int,
    val colunas: Int,
    val bombas: Int
)

fun salvarRecorde(context: Context, partida: PartidaRecorde) {
    val prefs = context.getSharedPreferences("RankingPrefs", Context.MODE_PRIVATE)
    val recordsAntigos = prefs.getString("top10", "") ?: ""

    val novaPartida = "${partida.nomeJogador}|${partida.jogadas}|${partida.tempoSegundos}|${partida.dataHora}|${partida.nivel}"
    val lista = if (recordsAntigos.isEmpty()) mutableListOf() else recordsAntigos.split(";;").toMutableList()
    lista.add(novaPartida)

    lista.sortBy {
        val dados = it.split("|")
        val tempo = dados.getOrNull(2)?.toIntOrNull() ?: 9999
        val joga = dados.getOrNull(1)?.toIntOrNull() ?: 9999
        (tempo * 1000) + joga
    }

    val top10 = lista.take(10)
    prefs.edit().putString("top10", top10.joinToString(";;")).apply()
}

fun carregarRanking(context: Context): List<PartidaRecorde> {
    val prefs = context.getSharedPreferences("RankingPrefs", Context.MODE_PRIVATE)
    val recordsAntigos = prefs.getString("top10", "") ?: ""
    if (recordsAntigos.isEmpty()) return emptyList()

    return recordsAntigos.split(";;").map {
        val dados = it.split("|")
        PartidaRecorde(
            nomeJogador = dados.getOrNull(0) ?: "Desconhecido",
            jogadas = dados.getOrNull(1)?.toIntOrNull() ?: 0,
            tempoSegundos = dados.getOrNull(2)?.toIntOrNull() ?: 0,
            dataHora = dados.getOrNull(3) ?: "",
            nivel = dados.getOrNull(4) ?: "Desconhecido"
        )
    }
}

class MotorCampoMinado(val linhas: Int = 10, val colunas: Int = 10, val totalMinas: Int = 15) {
    val matriz = Array(linhas) { l -> Array(colunas) { c -> CelulaEstado(l, c) } }

    init {
        reiniciarJogo()
    }

    fun reiniciarJogo() {
        for (l in 0 until linhas) {
            for (c in 0 until colunas) {
                matriz[l][c].valor = 0
                matriz[l][c].revelada = false
                matriz[l][c].marcacao = 0
            }
        }

        var minasPlantadas = 0
        val maxMinas = minOf(totalMinas, (linhas * colunas) - 1)

        while (minasPlantadas < maxMinas) {
            val l = (0 until linhas).random()
            val c = (0 until colunas).random()
            if (matriz[l][c].valor != -1) {
                matriz[l][c].valor = -1
                minasPlantadas++
            }
        }

        for (l in 0 until linhas) {
            for (c in 0 until colunas) {
                if (matriz[l][c].valor == -1) continue

                var minasAoRedor = 0
                for (i in -1..1) {
                    for (j in -1..1) {
                        val vl = l + i
                        val vc = c + j
                        if (vl in 0 until linhas && vc in 0 until colunas) {
                            if (matriz[vl][vc].valor == -1) minasAoRedor++
                        }
                    }
                }
                matriz[l][c].valor = minasAoRedor
            }
        }
    }

    fun abrirVizinhosVazios(l: Int, c: Int) {
        if (l !in 0 until linhas || c !in 0 until colunas) return
        val celula = matriz[l][c]

        if (celula.revelada || celula.marcacao == 1 || celula.valor == -1) return

        celula.revelada = true

        if (celula.valor == 0) {
            for (i in -1..1) {
                for (j in -1..1) {
                    abrirVizinhosVazios(l + i, c + j)
                }
            }
        }
    }
}

@Composable
fun CampoMinadoApp(modifier: Modifier = Modifier) {
    var configSelecionada by remember { mutableStateOf<DificuldadeConfig?>(null) }
    var mostrarCustomDialog by remember { mutableStateOf(false) }

    if (configSelecionada == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "💣 Campo Minado", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = { configSelecionada = DificuldadeConfig("Fácil", 10, 10, 15) }, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("Fácil (10x10 - 15 bombas)")
            }
            Button(onClick = { configSelecionada = DificuldadeConfig("Médio", 16, 16, 46) }, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("Médio (16x16 - 18% / 46 bombas)")
            }
            Button(onClick = { configSelecionada = DificuldadeConfig("Difícil", 16, 30, 100) }, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("Difícil (16x30 - 21% / 100 bombas)")
            }
            Button(onClick = { mostrarCustomDialog = true }, modifier = Modifier.fillMaxWidth().padding(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                Text("Personalizado (MAX 30x30 - MIN 5x5)")
            }
        }

        if (mostrarCustomDialog) {
            var lInput by remember { mutableStateOf("10") }
            var cInput by remember { mutableStateOf("10") }
            var mInput by remember { mutableStateOf("10") }

            AlertDialog(
                onDismissRequest = { mostrarCustomDialog = false },
                title = { Text("Modo Personalizado") },
                text = {
                    Column {
                        OutlinedTextField(value = lInput, onValueChange = { lInput = it }, label = { Text("Linhas") })
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = cInput, onValueChange = { cInput = it }, label = { Text("Colunas") })
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = mInput, onValueChange = { mInput = it }, label = { Text("Bombas") })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        var linhas = lInput.toIntOrNull() ?: 10
                        var colunas = cInput.toIntOrNull() ?: 10
                        var bombas = mInput.toIntOrNull() ?: 10

                        linhas = linhas.coerceIn(5, 30)
                        colunas = colunas.coerceIn(5, 30)

                        val maxBombas = (linhas * colunas) - 1
                        bombas = bombas.coerceIn(1, maxBombas)

                        configSelecionada = DificuldadeConfig("Personalizado", linhas, colunas, bombas)
                        mostrarCustomDialog = false
                    }) {
                        Text("Jogar")
                    }
                }
            )
        }
    } else {
        CampoMinadoScreen(
            config = configSelecionada!!,
            onVoltarMenu = { configSelecionada = null },
            modifier = modifier
        )
    }
}

@Composable
fun CampoMinadoScreen(config: DificuldadeConfig, onVoltarMenu: () -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current

    val activity = contexto as? Activity
    DisposableEffect(config.colunas) {
        if (config.colunas > 12) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val motor = remember(config) { MotorCampoMinado(config.linhas, config.colunas, config.bombas) }

    var jogadas by remember { mutableStateOf(0) }
    var tempoSegundos by remember { mutableStateOf(0) }
    var jogoRodando by remember { mutableStateOf(false) }
    var mensagemFim by remember { mutableStateOf("") }

    var mostrarDialogNome by remember { mutableStateOf(false) }
    var nomeDigitado by remember { mutableStateOf("") }

    var mostrarRanking by remember { mutableStateOf(false) }
    var listaRanking by remember { mutableStateOf(listOf<PartidaRecorde>()) }

    LaunchedEffect(jogoRodando) {
        if (jogoRodando) {
            while (true) {
                delay(1000)
                tempoSegundos++
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onVoltarMenu) {
                Text("← Menu")
            }
            RelogioNoTopo()
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Jogadas: $jogadas", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val min = tempoSegundos / 60
            val seg = tempoSegundos % 60
            Text(text = String.format(Locale.getDefault(), "Tempo: %02d:%02d", min, seg), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (mensagemFim.isNotEmpty()) {
            Text(
                text = mensagemFim,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (mensagemFim.contains("VENCEU")) Color(0xFF009900) else Color.Red
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val scrollHorizontal = rememberScrollState() // Permite arrastar pros lados
            LazyColumn(modifier = Modifier.horizontalScroll(scrollHorizontal)) {
                items(config.linhas) { l ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        for (c in 0 until config.colunas) {
                            val celula = motor.matriz[l][c]

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .padding(1.dp)
                                    .background(
                                        if (celula.revelada && celula.valor == -1) Color.Red
                                        else if (celula.revelada) Color.LightGray
                                        else Color.DarkGray
                                    )
                                    .border(1.dp, Color.Black)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                if (mensagemFim.isNotEmpty() || celula.revelada || celula.marcacao == 1) return@detectTapGestures

                                                if (!jogoRodando) jogoRodando = true
                                                jogadas++

                                                if (celula.valor == -1) {
                                                    celula.revelada = true
                                                    jogoRodando = false
                                                    mensagemFim = "Perdeu... não desista!"
                                                    motor.matriz.flatten().filter { it.valor == -1 }.forEach { it.revelada = true }
                                                } else {
                                                    motor.abrirVizinhosVazios(l, c)

                                                    val totalCasas = config.linhas * config.colunas
                                                    val casasReveladas = motor.matriz.flatten().count { it.revelada }
                                                    if (casasReveladas == totalCasas - config.bombas) {
                                                        jogoRodando = false
                                                        mensagemFim = "Parabéns, você VENCEU!"
                                                        mostrarDialogNome = true
                                                    }
                                                }
                                            },
                                            onLongPress = {
                                                if (mensagemFim.isNotEmpty() || celula.revelada) return@detectTapGestures
                                                if (!jogoRodando) jogoRodando = true

                                                jogadas++

                                                celula.marcacao = if (celula.marcacao == 0) 1 else 0
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!celula.revelada) {
                                    if (celula.marcacao == 1) Text("🚩", fontSize = 12.sp)
                                } else {
                                    if (celula.valor == -1) Text("💣", fontSize = 12.sp)
                                    else if (celula.valor > 0) Text(celula.valor.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (mensagemFim.isNotEmpty()) {
            Button(
                onClick = {
                    motor.reiniciarJogo()
                    jogadas = 0
                    tempoSegundos = 0
                    jogoRodando = false
                    mensagemFim = ""
                },
                modifier = Modifier.padding(4.dp)
            ) {
                Text("Começar Novamente")
            }
        }

        Button(
            onClick = {
                listaRanking = carregarRanking(contexto)
                mostrarRanking = true
            },
            modifier = Modifier.padding(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("Ver Ranking Top 10")
        }
    }

    if (mostrarDialogNome) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Novo Recorde!") },
            text = {
                Column {
                    Text("Você venceu o nível ${config.nome} em $tempoSegundos segundos com $jogadas jogadas.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nomeDigitado,
                        onValueChange = { nomeDigitado = it },
                        label = { Text("Digite seu nome") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nomeDigitado.isNotBlank()) {
                            val dataAtual = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date())
                            val novaPartida = PartidaRecorde(nomeDigitado, jogadas, tempoSegundos, dataAtual, config.nome)
                            salvarRecorde(contexto, novaPartida)
                            mostrarDialogNome = false
                            nomeDigitado = ""
                            listaRanking = carregarRanking(contexto)
                            mostrarRanking = true
                        }
                    }
                ) {
                    Text("Salvar")
                }
            }
        )
    }

    if (mostrarRanking) {
        AlertDialog(
            onDismissRequest = { mostrarRanking = false },
            title = { Text("🏆 TOP 10 Melhores Partidas") },
            text = {
                if (listaRanking.isEmpty()) {
                    Text("Nenhum recorde ainda. Seja o primeiro a vencer!")
                } else {
                    LazyColumn {
                        itemsIndexed(listaRanking) { index, partida ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "${index + 1}º - ${partida.nomeJogador}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(text = "Nível: ${partida.nivel} | Tempo: ${partida.tempoSegundos}s | Jogadas: ${partida.jogadas}", fontSize = 14.sp)
                                    Text(text = partida.dataHora, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mostrarRanking = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
fun RelogioNoTopo() {
    var horaAtual by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val formato = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            horaAtual = formato.format(Date())
            delay(1000)
        }
    }
    Text(text = horaAtual, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}