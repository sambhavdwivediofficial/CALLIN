package com.sambhavdwivedi.callin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sambhavdwivedi.callin.ui.theme.CallinTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            CallinTheme {
                WelcomeScreen()
            }
        }
    }
}

private val WelcomeFont = FontFamily(
    Font(
        resId = R.font.montserrat_extra_bold,
        weight = FontWeight.ExtraBold
    )
)

private val CallinFont = FontFamily(
    Font(
        resId = R.font.great_vibes_regular,
        weight = FontWeight.Normal
    )
)

@Composable
fun WelcomeScreen() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        /*
         * WELCOME
         *
         * statusBarsPadding() makes sure this text
         * starts BELOW the status bar.
         */
        Text(
            text = "Welcome",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 28.dp),
            color = Color.White,
            fontFamily = WelcomeFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 48.sp,
            letterSpacing = 1.sp
        )

        Text(
            text = "Hello Callin!",
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 4.dp),
            color = Color.White,
            fontFamily = CallinFont,
            fontWeight = FontWeight.Normal,
            fontSize = 40.sp,
            letterSpacing = 0.sp
        )
    }
}
