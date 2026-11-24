package com.ciberstore.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** `10.0.2.2` e o alias do host dentro do emulador Android. */
private const val BASE_URL = "http://10.0.2.2:8099"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(criarViewModel(BASE_URL))
        }
    }
}
