package com.yaadein.core.ui.media

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Coil model for an encrypted media file. The app module registers a fetcher that looks up the
 * media row, decrypts the thumbnail (or full-size file) via MediaCrypto and hands Coil the bytes.
 * Keeping the model here lets every feature show encrypted images without depending on :core:data.
 */
data class EncryptedImage(val mediaId: Long, val thumbnail: Boolean) {
    val cacheKey: String get() = "enc:$mediaId:${if (thumbnail) "t" else "f"}"
}

/** When set (previews, screenshot tests), images are drawn with this painter instead of Coil. */
val LocalPreviewImagePainter = staticCompositionLocalOf<((Long) -> Painter)?> { null }

@Composable
fun ProvidePreviewImages(painter: (Long) -> Painter, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPreviewImagePainter provides painter, content = content)
}

@Composable
fun ThumbnailImage(
    mediaId: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    EncryptedMediaImage(mediaId = mediaId, thumbnail = true, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}

@Composable
fun EncryptedMediaImage(
    mediaId: Long,
    thumbnail: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val preview = LocalPreviewImagePainter.current
    if (preview != null) {
        Image(painter = preview(mediaId), contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
        return
    }
    AsyncImage(
        model = EncryptedImage(mediaId = mediaId, thumbnail = thumbnail),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
