package dev.fastmc.jartools.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

fun <T> Flow<T>.split(scope: CoroutineScope): Pair<Flow<T>, Flow<T>> {
    val channelA = Channel<T>(Channel.UNLIMITED)
    val channelB = Channel<T>(Channel.UNLIMITED)
    scope.launch {
        collect {
            channelA.send(it)
            channelB.send(it)
        }
        channelA.close()
        channelB.close()
    }
    return channelA.consumeAsFlow() to channelB.consumeAsFlow()
}