package com.faunary.app.ui.components

import java.io.File

actual fun photoModel(pathOrUrl: String): Any = if (pathOrUrl.startsWith("http")) pathOrUrl else File(pathOrUrl)
