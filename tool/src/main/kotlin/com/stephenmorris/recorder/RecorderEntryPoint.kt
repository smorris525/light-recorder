package com.stephenmorris.recorder

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec
import kotlinx.coroutines.flow.StateFlow

/** Recorder's entry point: Tool Manager lists the exports, for download and delete (spec §13). */
@EntryPoint
object RecorderEntryPoint : LightEntryPoint {
    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) = Unit

    override suspend fun onPushNotification(data: ByteArray) = Unit

    /** One folder, Exports. Not backed up: LightOS backs up append-only, and an export is made again from its song. */
    override fun getToolManagerManifest(): ClientToolManifest = ClientToolManifest(
        title = "Recorder",
        roots = listOf(ClientLeafNode(FileBrowserSpec(label = "Exports", path = "exports"), canBeBackedUp = false)),
    )
}
