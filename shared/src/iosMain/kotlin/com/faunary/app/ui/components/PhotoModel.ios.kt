package com.faunary.app.ui.components

import com.faunary.app.data.localPhotoPath
import okio.Path.Companion.toPath

actual fun photoModel(pathOrUrl: String): Any = if (pathOrUrl.startsWith("http")) pathOrUrl else localPhotoPath(pathOrUrl).toPath()
