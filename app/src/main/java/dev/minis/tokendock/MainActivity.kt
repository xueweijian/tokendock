package dev.minis.tokendock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import dev.minis.tokendock.data.Store
import dev.minis.tokendock.sync.SyncEngine
import dev.minis.tokendock.sync.SyncScheduler
import dev.minis.tokendock.widget.refreshAllWidgets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var ocKey by remember { mutableStateOf<String?>(null) }
    var glmKey by remember { mutableStateOf<String?>(null) }
    var interval by remember { mutableStateOf("60") }
    var busy by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        val state = Store.read(context)
        ocKey = state.opencodeKey
        glmKey = state.glmKey
        interval = state.intervalMinutes.toString()
        // 启动静默补一次同步：解决"进程被冻/杀后周期任务迟迟不跑"的陈旧数据
        if (state.configured) SyncScheduler.syncNow(context)
    }

    /** 真同步：直接调引擎，全程阻塞等待，完成后回显真实结果 + 刷组件 */
    fun syncDirect() {
        busy = true
        scope.launch {
            Store.setRefreshing(context, System.currentTimeMillis())
            runCatching { refreshAllWidgets(context) } // 组件按钮先变"同步中"
            val result = SyncEngine.sync(context)
            Store.setRefreshing(context, 0L)
            busy = false
            val msg = when {
                !result.anyAttempted -> "尚未配置 API Key"
                result.allOk -> buildString {
                    append("同步成功")
                    result.opencode?.ocRolling?.let { append(" · OC 5h ${it.percent}%") }
                    result.glm?.glmTokens5h?.let { append(" · GLM 5h ${it.percent}%") }
                }
                else -> "同步失败：" + listOfNotNull(
                    result.opencode?.takeIf { !it.ok }?.errorMessage,
                    result.glm?.takeIf { !it.ok }?.errorMessage,
                ).joinToString(" / ")
            }
            snackbar.showSnackbar(msg)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE8C36A),
            background = Color(0xFF17191E),
            surface = Color(0xFF1E2128),
        ),
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = Color(0xFF17191E),
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                Text("TokenDock", fontSize = 26.sp, color = Color(0xFFE8C36A))
                Text(
                    "OpenCode Go / GLM Coding Plan 额度小组件",
                    fontSize = 13.sp,
                    color = Color(0xFF8A8F9A),
                )
                Spacer(Modifier.height(6.dp))

                KeyField(
                    title = "OpenCode Go API Key",
                    hint = "sk-...（opencode.ai/go）",
                    value = ocKey,
                    onValueChange = { ocKey = it },
                )
                KeyField(
                    title = "GLM Coding Plan API Key",
                    hint = "（bigmodel.cn 国内站）",
                    value = glmKey,
                    onValueChange = { glmKey = it },
                )

                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("自动刷新间隔（分钟，15-720）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF5F6F8),
                        unfocusedTextColor = Color(0xFFF5F6F8),
                        focusedBorderColor = Color(0xFFE8C36A),
                        unfocusedBorderColor = Color(0xFF3A3E48),
                        cursorColor = Color(0xFFE8C36A),
                    ),
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                Store.saveKeys(context, ocKey.orEmpty(), glmKey.orEmpty())
                                SyncScheduler.schedule(context, interval.toIntOrNull() ?: 60)
                                syncDirect()
                            }
                        },
                        enabled = !busy,
                    ) { Text("保存并同步") }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { syncDirect() },
                        enabled = !busy,
                    ) { Text("手动同步") }
                    if (busy) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.height(24.dp).width(24.dp),
                        )
                    }
                }

                Text(
                    "• Key 只存在本机 DataStore，不上传任何第三方服务器\n" +
                        "• 三种小组件（大字 / 双环 / 数据表）可在桌面长按挑选\n" +
                        "• 点小组件右上角 ⟳ 立即刷新，无需打开 app\n" +
                        "• 后台按设定间隔自动刷新（受系统调度影响可能有延迟）\n" +
                        "• OpenCode 接口内置浏览器 UA（绕过 Cloudflare）；GLM 走国内站端点",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF6E737E),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyField(
    title: String,
    hint: String,
    value: String?,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text(title, fontSize = 14.sp, color = Color(0xFFD7DAE0))
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value ?: "",
            onValueChange = onValueChange,
            placeholder = { Text(hint, color = Color(0xFF6E737E)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFFF5F6F8),
                unfocusedTextColor = Color(0xFFF5F6F8),
                focusedBorderColor = Color(0xFFE8C36A),
                unfocusedBorderColor = Color(0xFF3A3E48),
                cursorColor = Color(0xFFE8C36A),
            ),
        )
    }
}
