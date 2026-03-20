package junzi.iwara

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppRoute
import junzi.iwara.model.AppUiState

private const val REGISTER_URL = "https://www.iwara.tv/register"

@Composable
fun IwaraApp(controller: IwaraAppController) {
    val state by controller.state.collectAsState()

    LaunchedEffect(Unit) {
        controller.bootstrap()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            BrowserRuntimeHost()
            when {
                state.bootstrapping -> SplashScreen()
                state.route == AppRoute.Login -> LoginScreen(state, controller)
                state.route == AppRoute.Feed -> FeedScreen(state, controller)
                state.route == AppRoute.Search -> SearchScreen(state, controller)
                state.route == AppRoute.Profile -> ProfileScreen(state, controller)
                state.route == AppRoute.Player -> PlayerScreen(state, controller)
                state.route == AppRoute.Playlist -> PlaylistScreen(state, controller)
            }
        }
    }
}

@Composable
private fun BrowserRuntimeHost() {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                BrowserBridge.attach(this)
            }
        },
        modifier = Modifier
            .padding(0.dp)
            .height(1.dp)
            .fillMaxWidth(),
        update = { BrowserBridge.attach(it) },
    )
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun LoginScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.label_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.label_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { controller.login(email, password) },
            enabled = !state.loginInFlight && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.loginInFlight) stringResource(R.string.action_signing_in)
                else stringResource(R.string.action_sign_in),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REGISTER_URL))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.action_register_open_web))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_register))
        }
        if (state.loginError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.loginError,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

