package studio.sparkcube.cadence.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.sparkcube.cadence.core.bookmark.UserBookmark

/** Add / list / jump / delete curated bookmarks for the current document. */
@Composable
fun BookmarkBar(state: ReaderState) {
    var name by remember { mutableStateOf("") }
    fun add() { state.addBookmark(name); name = "" }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                placeholder = { Text("Bookmark this spot…", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { add() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CadenceColors.Accent),
            ) { Text("＋ Add", color = Color.White) }
        }

        if (state.bookmarkList.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.bookmarkList.forEach { bm ->
                    BookmarkChip(bm, onJump = { state.jumpToBookmark(bm) }, onDelete = { state.removeBookmark(bm) })
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
private fun BookmarkChip(bm: UserBookmark, onJump: () -> Unit, onDelete: () -> Unit) {
    Surface(color = CadenceColors.AccentSoft, shape = RoundedCornerShape(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                "p${bm.page} · ${bm.label}",
                fontSize = 12.sp,
                color = CadenceColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp).clickable { onJump() },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "✕",
                fontSize = 13.sp,
                color = CadenceColors.Muted,
                modifier = Modifier.clickable { onDelete() }.padding(horizontal = 2.dp),
            )
        }
    }
}
