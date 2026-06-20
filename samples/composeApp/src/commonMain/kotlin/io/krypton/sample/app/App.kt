package io.krypton.sample.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.krypton.core.types.DeviceId
import io.krypton.core.types.ProtocolAddress
import io.krypton.protocol.api.KryptonConfigurator
import io.krypton.protocol.api.KryptonProtocol
import io.krypton.protocol.api.decrypt
import io.krypton.protocol.api.encrypt
import io.krypton.storage.memory.InMemoryPreKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-platform label for the status line. */
expect fun platformName(): String

private data class ChatMsg(val from: String, val text: String, val wire: String)

private class Chat(val alice: KryptonProtocol, val bob: KryptonProtocol)

private suspend fun newParty(): KryptonProtocol {
    val pk = InMemoryPreKeyStore()
    val p = KryptonConfigurator().apply { preKeyStore = pk }.build()
    pk.saveSignedPreKey(1, p.generateSignedPreKey(1).getOrThrow()).getOrThrow()
    p.generatePreKeys(1, 10).getOrThrow().forEach { pk.storePreKey(it.keyId, it).getOrThrow() }
    return p
}

private suspend fun setupChat(): Chat {
    val alice = newParty()
    val bob = newParty()
    bob.processPreKeyBundle(alice.getPreKeyBundle(ProtocolAddress("alice", DeviceId(1))).getOrThrow()).getOrThrow()
    alice.processPreKeyBundle(bob.getPreKeyBundle(ProtocolAddress("bob", DeviceId(1))).getOrThrow()).getOrThrow()
    return Chat(alice, bob)
}

/** Encrypts [text] from [from] and verifies the peer decrypts it; returns the bubble. */
private suspend fun exchange(c: Chat, from: String, text: String): ChatMsg {
    val to = if (from == "Alice") "bob" else "alice"
    val encP = if (from == "Alice") c.alice else c.bob
    val decP = if (from == "Alice") c.bob else c.alice
    val wire = encP.encrypt(to, text).getOrThrow()
    decP.decrypt(from.lowercase(), wire).getOrThrow() // prove the peer really decrypts it
    return ChatMsg(from, text, wire)
}

/** The shared chat UI — identical on desktop and iOS. */
@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val scope = rememberCoroutineScope()
        var chat by remember { mutableStateOf<Chat?>(null) }
        var status by remember { mutableStateOf("Setting up sessions…") }
        val messages = remember { mutableStateListOf<ChatMsg>() }
        var input by remember { mutableStateOf("") }
        var sender by remember { mutableStateOf("Alice") }
        val listState = rememberLazyListState()

        LaunchedEffect(Unit) {
            runCatching {
                withContext(Dispatchers.Default) {
                    val c = setupChat()
                    // Seed a short real-encrypted exchange so the wire is visible at a glance.
                    val seed = listOf(
                        exchange(c, "Alice", "Hi Bob! 👋 This is end-to-end encrypted."),
                        exchange(c, "Bob", "Got it, Alice 🔐 real libsignal on-device."),
                        exchange(c, "Alice", "Every bubble shows the ciphertext that travels."),
                    )
                    c to seed
                }
            }
                .onSuccess { (c, seed) ->
                    chat = c
                    messages.addAll(seed)
                    status = "Real libsignal · ${platformName()} · X3DH session established"
                }
                .onFailure { status = "Setup failed: ${it.message}" }
        }

        fun send() {
            val c = chat ?: return
            val text = input.trim()
            if (text.isEmpty()) return
            input = ""
            val from = sender
            scope.launch {
                runCatching {
                    val msg = withContext(Dispatchers.Default) { exchange(c, from, text) }
                    messages.add(msg)
                    listState.animateScrollToItem(messages.size)
                }.onFailure { status = "Error: ${it.message}" }
            }
        }

        Scaffold(
            topBar = {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("🔐 Krypton Encrypted Chat", style = MaterialTheme.typography.titleLarge)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                if (chat == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages) { msg -> Bubble(msg) }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = sender == "Alice", onClick = { sender = "Alice" }, label = { Text("Alice") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = sender == "Bob", onClick = { sender = "Bob" }, label = { Text("Bob") })
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Message as $sender…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = ::send) { Text("Send 🔒") }
                }
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMsg) {
    val mine = msg.from == "Alice"
    val align = if (mine) Alignment.Start else Alignment.End
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(msg.from, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(msg.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.padding(2.dp))
                Text(
                    "🔒 ${msg.wire.take(40)}…  (${msg.wire.length} chars)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
