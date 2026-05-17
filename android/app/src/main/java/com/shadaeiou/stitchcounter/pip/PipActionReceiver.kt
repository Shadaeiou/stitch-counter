package com.shadaeiou.stitchcounter.pip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val ACTION_PIP_INCREMENT = "com.shadaeiou.stitchcounter.PIP_INCREMENT"
const val ACTION_PIP_DECREMENT = "com.shadaeiou.stitchcounter.PIP_DECREMENT"

class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PIP_INCREMENT -> PipEvents.actions.tryEmit(PipAction.INCREMENT)
            ACTION_PIP_DECREMENT -> PipEvents.actions.tryEmit(PipAction.DECREMENT)
        }
    }
}
