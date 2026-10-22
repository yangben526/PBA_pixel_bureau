package app.pixelbureau.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pixelbureau.Pixel_Bureau_Web.shared.SoftGameButton
import app.pixelbureau.R
import app.pixelbureau.logic.hireArrivalLine

private val DialogShape = RoundedCornerShape(22.dp)
private val PortraitShape = RoundedCornerShape(16.dp)

/**
 * Next-day HR line. The portrait is pixel art; the sentence is drawn in text
 * so the candidate count stays exact.
 */
@Composable
internal fun HireArrivalDialog(
    count: Int,
    onDismiss: () -> Unit,
    onSee: () -> Unit,
) {
    val line = hireArrivalLine(count)
    val context = LocalContext.current
    val portrait = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.hire_portrait_hr).asImageBitmap()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(DialogShape)
                .background(KaiCard)
                .border(2.dp, KaiBorderDeep, DialogShape)
                .padding(16.dp)
                .semantics { contentDescription = "HR. $line" },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    bitmap = portrait,
                    contentDescription = "HR",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(PortraitShape)
                        .border(2.dp, KaiBorder, PortraitShape),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.None,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(KaiMint)
                            .border(1.5.dp, KaiMintDeep, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("HR", color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text(
                        line,
                        color = KaiInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
            SoftGameButton(
                label = "See candidates",
                enabled = true,
                face = KaiAccent,
                highlight = KaiAccentHi,
                shadow = KaiAccentLo,
                ink = Ink,
                onClick = onSee,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
