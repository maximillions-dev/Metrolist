/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.constants.DiscordTokenKey
import com.metrolist.music.constants.DiscordUseDetailsKey
import com.metrolist.music.constants.EnableDiscordRPCKey
import com.metrolist.music.db.entities.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope

class DiscordRPCManager(
    private val context: Context,
    private val getCurrentPosition: () -> Long,
) {
    private var rpc: DiscordRPC? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val eventChannel = Channel<RpcEvent>(Channel.UNLIMITED)

    private var currentSong: Song? = null
    private var isPlaying = false
    private var currentSpeed = 1.0f
    private var useDetails = false

    init {
        // Listen for connection changes
        scope.launch {
            context.dataStore.data
                .map { it[DiscordTokenKey] to (it[EnableDiscordRPCKey] ?: true) }
                .distinctUntilChanged()
                .collect { (token, enabled) ->
                    eventChannel.send(RpcEvent.UpdateConnection(token, enabled))
                }
        }
        // Listen for details setting changes
        scope.launch {
            context.dataStore.data
                .map { it[DiscordUseDetailsKey] ?: false }
                .distinctUntilChanged()
                .collect { useDetails ->
                    eventChannel.send(RpcEvent.UpdateUseDetails(useDetails))
                }
        }
        // Process all events sequentially
        scope.launch {
            eventChannel.receiveAsFlow().collect { event ->
                handleEvent(event)
            }
        }
        // Periodic updater
        scope.launch {
            while(isActive) {
                delay(15000) // Update every 15 seconds
                if (isPlaying) {
                    eventChannel.send(RpcEvent.ForceUpdate)
                }
            }
        }
    }

    private suspend fun handleEvent(event: RpcEvent) {
        when (event) {
            is RpcEvent.SongChanged -> currentSong = event.song
            is RpcEvent.PlaybackStateChanged -> isPlaying = event.isPlaying
            is RpcEvent.SpeedChanged -> currentSpeed = event.speed
            is RpcEvent.UpdateConnection -> {
                rpc?.closeRPC()
                rpc = null
                if (event.enabled && event.token != null) {
                    rpc = DiscordRPC(context, event.token)
                }
            }
            is RpcEvent.UpdateUseDetails -> useDetails = event.useDetails
            RpcEvent.ForceUpdate -> { /* This just forces an update with current state */ }
        }
        updateRpcPresence()
    }

    private suspend fun updateRpcPresence() {
        val rpcInstance = rpc ?: return

        val song = currentSong
        if (isPlaying && song != null) {
            // Ensure we're on a background thread for the network call
            withContext(Dispatchers.IO) {
                rpcInstance.updateSong(song, getCurrentPosition(), currentSpeed, useDetails)
            }
        } else {
            withContext(Dispatchers.IO) {
                rpcInstance.close()
            }
        }
    }

    fun onSongChanged(song: Song?) {
        scope.launch { eventChannel.send(RpcEvent.SongChanged(song)) }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        scope.launch { eventChannel.send(RpcEvent.PlaybackStateChanged(isPlaying)) }
    }

    fun onSpeedChanged(speed: Float) {
        scope.launch { eventChannel.send(RpcEvent.SpeedChanged(speed)) }
    }

    fun forceUpdate() {
        scope.launch { eventChannel.send(RpcEvent.ForceUpdate) }
    }

    fun destroy() {
        scope.cancel()
        GlobalScope.launch(Dispatchers.IO) {
            rpc?.closeRPC()
        }
    }
}

private sealed class RpcEvent {
    data class SongChanged(val song: Song?) : RpcEvent()
    data class PlaybackStateChanged(val isPlaying: Boolean) : RpcEvent()
    data class SpeedChanged(val speed: Float) : RpcEvent()
    data class UpdateConnection(val token: String?, val enabled: Boolean) : RpcEvent()
    data class UpdateUseDetails(val useDetails: Boolean) : RpcEvent()
    object ForceUpdate : RpcEvent()
}
