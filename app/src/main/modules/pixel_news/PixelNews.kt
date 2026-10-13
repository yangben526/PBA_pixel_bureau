package app.pixelbureau.modules.pixelnews

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixelbureau.R
import app.pixelbureau.ui.KaiBorder
import app.pixelbureau.ui.KaiCard
import app.pixelbureau.ui.KaiInk
import app.pixelbureau.ui.KaiInkMuted

data class PixelStory(
    val id: String,
    val tag: String,
    val tagFace: Color,
    val date: String,
    val title: String,
    val summary: String,
    val image: Int,
)

object PixelNews {
    const val SeeAllId = "news"

    val stories: List<PixelStory> = listOf(
        PixelStory(
            id = "news-desks",
            tag = "HOT",
            tagFace = Color(0xFFE25B4A),
            date = "Apr 24, 2025",
            title = "Best Office Desks of 2025",
            summary = "Level up your workspace with these top picks for productivity.",
            image = R.drawable.portal_news_desk,
        ),
        PixelStory(
            id = "news-plants",
            tag = "TREND",
            tagFace = Color(0xFF3D7EDB),
            date = "Apr 22, 2025",
            title = "Top 10 Desk Plants for a Happier You",
            summary = "Bring more green to your workspace.",
            image = R.drawable.portal_news_plant,
        ),
        PixelStory(
            id = "news-coffee",
            tag = "NEW",
            tagFace = Color(0xFF3E9A4A),
            date = "Apr 20, 2025",
            title = "The Ultimate Coffee Machine Guide",
            summary = "Brew better ideas, one cup at a time.",
            image = R.drawable.portal_news_coffee,
        ),
    )

    fun matching(query: String): List<PixelStory> {
        val q = query.trim()
        if (q.isEmpty()) return stories
        return stories.filter { story ->
            listOf(story.tag, story.title, story.summary, story.date)
                .any { it.contains(q, ignoreCase = true) }
        }
    }

    fun owns(placeId: String): Boolean =
        placeId == SeeAllId || stories.any { it.id == placeId }
}

private val NewsCardShape = RoundedCornerShape(18.dp)
private val NewsTile = RoundedCornerShape(14.dp)

@Composable
fun PixelNewsCard(
    items: List<PixelStory>,
    onOpen: (String) -> Unit,
    showHeader: Boolean = true,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NewsCardShape)
            .background(KaiCard)
            .border(1.5.dp, KaiBorder, NewsCardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.portal_news),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    "Pixel News",
                    color = KaiInk,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                Text(
                    "See All  ›",
                    color = Color(0xFF3D7EDB),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onOpen(PixelNews.SeeAllId) },
                    ),
                )
            }
        }
        if (items.isEmpty()) {
            Text("No stories match that.", color = KaiInkMuted, style = MaterialTheme.typography.bodySmall)
        }
        items.forEach { story ->
            StoryRow(story) { onOpen(story.id) }
        }
    }
}

@Composable
fun PixelNewsPlace(placeId: String, onOpen: (String) -> Unit) {
    val story = PixelNews.stories.firstOrNull { it.id == placeId }
    when {
        placeId == PixelNews.SeeAllId -> {
            NewsHeader(R.drawable.portal_news, "Pixel News", "Every desk story in one list.")
            PixelNewsCard(PixelNews.stories, onOpen, showHeader = false)
        }
        story != null -> StoryPage(story)
    }
}

@Composable
private fun StoryPage(story: PixelStory) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NewsCardShape)
            .background(KaiCard)
            .border(1.5.dp, KaiBorder, NewsCardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painterResource(story.image),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(177f / 96f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagChip(story.tag, story.tagFace)
            Spacer(Modifier.weight(1f))
            Text(story.date, color = KaiInkMuted, fontSize = 12.sp)
        }
        Text(story.title, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(story.summary, color = KaiInkMuted, style = MaterialTheme.typography.bodyMedium)
    }
    NewsShelf(
        "Story",
        "The rest of this piece isn't written yet.",
        "The write-up for this desk story will sit here.",
    )
}

@Composable
private fun StoryRow(story: PixelStory, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(NewsTile)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painterResource(story.image),
            contentDescription = null,
            modifier = Modifier
                .width(92.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip(story.tag, story.tagFace)
                Spacer(Modifier.weight(1f))
                Text(story.date, color = KaiInkMuted, fontSize = 11.sp)
            }
            Text(story.title, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp)
            Text(story.summary, color = KaiInkMuted, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun NewsHeader(image: Int, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(NewsCardShape)
            .background(KaiCard)
            .border(1.5.dp, KaiBorder, NewsCardShape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(image),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = KaiInk, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(detail, color = KaiInkMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun NewsShelf(title: String, empty: String, note: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NewsCardShape)
            .background(KaiCard)
            .border(1.5.dp, KaiBorder, NewsCardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = KaiInk, fontWeight = FontWeight.Bold)
        Text(empty, color = KaiInk, style = MaterialTheme.typography.bodyMedium)
        Text(note, color = KaiInkMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
    }
}

@Composable
private fun TagChip(label: String, face: Color) {
    Text(
        label,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(face)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
