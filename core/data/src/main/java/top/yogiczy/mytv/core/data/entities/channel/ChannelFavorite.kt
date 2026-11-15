package top.yogiczy.mytv.core.data.entities.channel

import kotlinx.serialization.Serializable

@Serializable
data class ChannelFavorite(
    val channel: Channel,
    val iptvSourceName: String,
    val groupName: String,
)