package dev.sebastiano.composefontenumerator

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FileFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.singleWindowApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun main() = singleWindowApplication(title = "Fonts enumerator") {
    var fontFamilies by remember { mutableStateOf(emptyList<SystemFontFamily>()) }
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            fontFamilies = listFontFamilies().sortedBy { it.name }
        }
    }

    if (fontFamilies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // We don't depend on the Material library — so, no fancy progress indicators for us
            BasicText("Loading...")
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            val scrollState = rememberLazyListState()
            LazyColumn(modifier = Modifier.fillMaxSize().padding(end = 8.dp), state = scrollState) {
                items(items = fontFamilies, key = { (name, _) -> name }) { (name, fontFamily, fonts) ->
                    FontFamilyItem(name, fontFamily, fonts)
                }
            }
            VerticalScrollbar(
                rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun FontFamilyItem(
    name: String,
    fontFamily: FontFamily,
    fonts: List<FileFont>
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
        BasicText(text = name, style = TextStyle.Default.copy(fontFamily = fontFamily, fontSize = 18.sp))

        Spacer(Modifier.height(8.dp))

        BasicText(
            text = "Files:",
            style = TextStyle.Default.copy(fontSize = 14.sp, color = TextStyle.Default.color.copy(alpha = .54f)),
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp)
        )

        for (fileFont in fonts) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                BasicText(
                    text = "W${fileFont.weight.weight} ${fileFont.style}",
                    style = TextStyle.Default.copy(
                        fontSize = 12.sp,
                        color = TextStyle.Default.color.copy(alpha = .54f),
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Spacer(Modifier.width(16.dp))

                BasicText(
                    text = fileFont.file.path,
                    style = TextStyle.Default.copy(fontSize = 12.sp, color = TextStyle.Default.color.copy(alpha = .54f)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray))
    }
}
