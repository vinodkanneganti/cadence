package studio.sparkcube.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Add / list / jump / delete bookmarks, shown in the ☆ popover. */
@Composable
fun BookmarksPanel(state: ReaderState) {
    val pal = LocalPalette.current
    var name by remember { mutableStateOf("") }
    fun add() { state.addBookmark(name); name = "" }

    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(18.dp)) {
        Text("BOOKMARKS", fontSize = 11.sp, letterSpacing = 1.sp, color = pal.faint, fontWeight = FontWeight.SemiBold)

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                placeholder = { Text("Name this spot…", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Surface(onClick = { add() }, shape = RoundedCornerShape(9.dp), color = pal.accent) {
                Text("＋ Add", color = pal.onAccent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState())) {
            if (state.bookmarkList.isEmpty()) {
                Text("No bookmarks yet. Add one at your current spot.", fontSize = 12.sp, color = pal.faint, modifier = Modifier.padding(vertical = 8.dp))
            }
            state.bookmarkList.forEach { bm ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .clickable { state.jumpToBookmark(bm) }.padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Column {
                            Text(bm.label, fontSize = 13.sp, color = pal.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text("Page ${bm.page}", fontSize = 11.sp, color = pal.faint)
                        }
                    }
                    Surface(onClick = { state.removeBookmark(bm) }, shape = RoundedCornerShape(8.dp), color = pal.card) {
                        Text("✕", color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}
