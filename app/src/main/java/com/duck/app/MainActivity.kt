package com.duck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.duck.app.data.prefs.PrefsHelper
import com.duck.app.ui.theme.PrefsHelperTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		val prefsHelper = PrefsHelper(this)
		prefsHelper.exampleNormalValue = "Hello"
		prefsHelper.exampleDeviceValue = "World"
		setContent {
			PrefsHelperTheme {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					val exampleDataStoreValue = prefsHelper.exampleDataStoreValueFlow.collectAsState("")
					Greeting(
						name = "Android, ${prefsHelper.exampleNormalValue} ${prefsHelper.exampleDeviceValue} ${exampleDataStoreValue.value}",
						modifier = Modifier.padding(innerPadding)
					)
				}
			}
		}
		lifecycleScope.launch {
			prefsHelper.exampleDataStoreValue = ""
			for (i in 1..3) {
				delay(2000)
				prefsHelper.exampleDataStoreValue += "!"
			}
		}
	}
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
	Text(
		text = "Hello $name!",
		modifier = modifier
	)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
	PrefsHelperTheme {
		Greeting("Android")
	}
}