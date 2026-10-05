package com.example.campominadokotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import android.content.Context
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
                CampoMinadoScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

class CelulaEstado(val linha: Int, val coluna: Int) {
    var valor by mutableStateOf(0) // -1 é bomba, 0 a 8 são os vizinhos
    var revelada by mutableStateOf(false)
    var temBandeira by mutableStateOf(false)
}

// essa parte aq é do salvamento e os top 10 vencedores.
data class PartidaRecorde(
    val nomeJogador: String,
    val jogadas: Int,
    val tempoSegundos: Int,
    val dataHora: String
)

fun salvarRecorde(context: Context, partida: PartidaRecorde) {
    val prefs = context.getSharedPreferences("RankingPrefs", Context.MODE_PRIVATE)
    val recordsAntigos = prefs.getString("top10", "") ?: ""

    val novaPartida = "${partida.nomeJogador}|${partida.jogadas}|${partida.tempoSegundos}|${partida.dataHora}"
    val lista = if (recordsAntigos.isEmpty()) mutableListOf() else recordsAntigos.split(";;").toMutableList()
    lista.add(novaPartida)

    // ordena pelo tempo. Se o tempo for igual, quem tiver menos jogadas vence.
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
    val prefs = context.getSharedPreferences("RankingPrefs", android.content.Context.MODE_PRIVATE)
    val recordsAntigos = prefs.getString("top10", "") ?: ""
    if (recordsAntigos.isEmpty()) return emptyList()

    return recordsAntigos.split(";;").map {
        val dados = it.split("|")
        PartidaRecorde(
            nomeJogador = dados.getOrNull(0) ?: "Desconhecido",
            jogadas = dados.getOrNull(1)?.toIntOrNull() ?: 0,
            tempoSegundos = dados.getOrNull(2)?.toIntOrNull() ?: 0,
            dataHora = dados.getOrNull(3) ?: ""
        )
    }
}

class MotorCampoMinado(val tamanho: Int = 10) {
    val matriz = Array(tamanho) { l -> Array(tamanho) { c -> CelulaEstado(l, c) } }

    init {
        reiniciarJogo()
    }

    fun reiniciarJogo() {
        for (l in 0 until tamanho) {
            for (c in 0 until tamanho) {
                matriz[l][c].valor = 0
                matriz[l][c].revelada = false
                matriz[l][c].temBandeira = false
            }
        }

        // parte da plantacao das bomba aleatoria.
        var minasPlantadas = 0
        val totalMinas = 15
        while (minasPlantadas < totalMinas) {
            val l = (0 until tamanho).random()
            val c = (0 until tamanho).random()
            if (matriz[l][c].valor != -1) {
                matriz[l][c].valor = -1
                minasPlantadas++
            }
        }

        // calcular os vizinho
        for (l in 0 until tamanho) {
            for (c in 0 until tamanho) {
                if (matriz[l][c].valor == -1) continue

                var minasAoRedor = 0
                for (i in -1..1) {
                    for (j in -1..1) {
                        val vl = l + i
                        val vc = c + j
                        if (vl in 0 until tamanho && vc in 0 until tamanho) {
                            if (matriz[vl][vc].valor == -1) minasAoRedor++
                        }
                    }
                }
                matriz[l][c].valor = minasAoRedor
            }
        }
    }

    fun abrirVizinhosVazios(l: Int, c: Int) {
        if (l !in 0 until tamanho || c !in 0 until tamanho) return
        val celula = matriz[l][c]

        if (celula.revelada || celula.temBandeira || celula.valor == -1) return

        celula.revelada = true // ao alterar a cor muda sozinha

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
fun CampoMinadoScreen(modifier: Modifier = Modifier) {

    val contexto = LocalContext.current

    val motor = remember { MotorCampoMinado() }
    var jogadas by remember { mutableStateOf(0) }
    var tempoSegundos by remember { mutableStateOf(0) }
    var jogoRodando by remember { mutableStateOf(false) }
    var mensagemFim by remember { mutableStateOf("") } // Guarda se ganhou ou perdeu

    var mostrarDialogNome by remember { mutableStateOf(false) }
    var nomeDigitado by remember { mutableStateOf("") }

    var mostrarRanking by remember { mutableStateOf(false) }
    var listaRanking by remember { mutableStateOf(listOf<PartidaRecorde>()) }

    // parte do cronometro
    LaunchedEffect(jogoRodando) {
        if (jogoRodando) {
            while (true) {
                delay(1000)
                tempoSegundos++
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RelogioNoTopo()

        // aqui fica parte do placar, jogadas e tambem tempo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Jogadas: $jogadas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val min = tempoSegundos / 60
            val seg = tempoSegundos % 60
            Text(text = String.format(Locale.getDefault(), "Tempo: %02d:%02d", min, seg), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }



        Spacer(modifier = Modifier.height(24.dp))

        if (mensagemFim.isNotEmpty()) {
            Text(
                text = mensagemFim,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (mensagemFim.contains("VENCEU")) Color(0xFF009900) else Color.Red
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // aqui a parte que desenha a matriz
        Column {
            for (l in 0 until motor.tamanho) {
                Row {
                    for (c in 0 until motor.tamanho) {
                        val celula = motor.matriz[l][c]

                        // aq é substitui o botao por um quadrado desenhado
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .padding(1.dp)
                                .background(

                                    if (celula.revelada && celula.valor == -1) Color.Red // Explodiu
                                    else if (celula.revelada) Color.LightGray
                                    else Color.DarkGray
                                )
                                .border(1.dp, Color.Black)
                                // sub do clique normal e longo
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            if (mensagemFim.isNotEmpty() || celula.revelada || celula.temBandeira) return@detectTapGestures

                                            if (!jogoRodando) jogoRodando = true
                                            jogadas++

                                            if (celula.valor == -1) {
                                                celula.revelada = true
                                                jogoRodando = false
                                                mensagemFim = "Perdeu... não desista!"
                                                // Revela todas as bombas ao perder
                                                motor.matriz.flatten().filter { it.valor == -1 }.forEach { it.revelada = true }
                                            } else {
                                                motor.abrirVizinhosVazios(l, c)
                                                // Condicao de vitoria 85 casas seguraas
                                                val abertas = motor.matriz.flatten().count { it.revelada }
                                                if (abertas == 1) {
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
                                            celula.temBandeira = !celula.temBandeira
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {

                            if (celula.temBandeira && !celula.revelada) {
                                Text("🚩")
                            } else if (celula.revelada) {
                                if (celula.valor == -1) Text("💣")
                                else if (celula.valor > 0) Text(celula.valor.toString(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        //fim do jogo com reiniciar pra limpar
        if (mensagemFim.isNotEmpty()) {
            Button(
                onClick = {
                    motor.reiniciarJogo()
                    jogadas = 0
                    tempoSegundos = 0
                    jogoRodando = false
                    mensagemFim = ""
                },
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Começar Novamente")
            }
        }

        // botao do ranking
        Button(
            onClick = {
                listaRanking = carregarRanking(contexto)
                mostrarRanking = true
            },
            modifier = Modifier.padding(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("Ver Ranking Top 10 (Vencedores)")
        }
    }

    if (mostrarDialogNome) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Novo Recorde!") },
            text = {
                Column {
                    Text("Você venceu em $tempoSegundos segundos com $jogadas jogadas.")
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


                            val novaPartida = PartidaRecorde(nomeDigitado, jogadas, tempoSegundos, dataAtual)


                            salvarRecorde(contexto, novaPartida)


                            mostrarDialogNome = false
                            nomeDigitado = ""

                            // atualizar os 10 automaticamente
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
                                    Text(text = "Tempo: ${partida.tempoSegundos}s | Jogadas: ${partida.jogadas}", fontSize = 14.sp)
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
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopEnd) {
        Text(text = horaAtual, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}