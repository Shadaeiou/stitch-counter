package com.shadaeiou.stitchcounter.pip

import kotlinx.coroutines.flow.MutableSharedFlow

enum class PipAction { INCREMENT, DECREMENT }

object PipEvents {
    val actions = MutableSharedFlow<PipAction>(extraBufferCapacity = 8)
}
