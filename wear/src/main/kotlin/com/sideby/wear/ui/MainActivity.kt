package com.sideby.wear.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.Wearable
import com.sideby.shared.constants.DataLayerConstants
import com.sideby.shared.model.PokeMessage
import com.sideby.shared.model.PokeType
import com.sideby.wear.data.WearDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SidebyWearApp(
                onSendPoke = { pokeType ->
                    sendPokeToPhone(pokeType)
                }
            )
        }
    }

    private fun sendPokeToPhone(type: PokeType) {
        val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(this@MainActivity)
                val nodes = nodeClient.connectedNodes.await()
                val messageClient = Wearable.getMessageClient(this@MainActivity)
                val payload = Json.encodeToString(PokeMessage(type, "Wear User")).toByteArray(Charsets.UTF_8)

                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, DataLayerConstants.PATH_POKE, payload).await()
                }

                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Terkirim: ${type.title}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Gagal kirim", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun SidebyWearApp(
    onSendPoke: (PokeType) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberScalingLazyListState()
    val partner by remember { mutableStateOf(WearDataManager.getPartnerStatus(context)) }

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Partner Name
                item {
                    Text(
                        text = "Sideby • ${partner.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF85A1)
                    )
                }

                // Mood & Status
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(text = partner.mood.emoji, fontSize = 32.sp)
                        Text(
                            text = partner.mood.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🔋 ${partner.phoneBattery.displayString} • ${partner.location.summary}",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }

                // Poke Action Row 1
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { onSendPoke(PokeType.HEARTBEAT) },
                            modifier = Modifier.size(42.dp),
                            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFFE91E63))
                        ) {
                            Text(text = "❤️", fontSize = 16.sp)
                        }

                        Button(
                            onClick = { onSendPoke(PokeType.HUG) },
                            modifier = Modifier.size(42.dp),
                            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFF673AB7))
                        ) {
                            Text(text = "🫂", fontSize = 16.sp)
                        }

                        Button(
                            onClick = { onSendPoke(PokeType.KISS) },
                            modifier = Modifier.size(42.dp),
                            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFFFF4081))
                        ) {
                            Text(text = "💋", fontSize = 16.sp)
                        }
                    }
                }

                // Poke Action Row 2
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { onSendPoke(PokeType.NUDGE) },
                            modifier = Modifier.size(42.dp),
                            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFF009688))
                        ) {
                            Text(text = "👉", fontSize = 16.sp)
                        }

                        Button(
                            onClick = { onSendPoke(PokeType.DRINK_WATER) },
                            modifier = Modifier.size(42.dp),
                            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFF03A9F4))
                        ) {
                            Text(text = "💧", fontSize = 16.sp)
                        }

                        Button(
                            onClick = { onSendPoke(PokeType.MISS_YOU) },
                            modifier = Modifier.size(42.dp),
                            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFFFF9800))
                        ) {
                            Text(text = "💌", fontSize = 16.sp)
                        }
                    }
                }

                // Love counter footer
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "❤️ Selalu Bersama",
                        fontSize = 11.sp,
                        color = Color(0xFFFF85A1),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
