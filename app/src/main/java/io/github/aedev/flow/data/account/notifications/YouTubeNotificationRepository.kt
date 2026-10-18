package io.github.aedev.flow.data.account.notifications

import io.github.aedev.flow.data.local.NotificationRepository
import io.github.aedev.flow.data.local.entity.NotificationEntity
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeNotificationRepository
    @Inject
    constructor(
        private val notificationRepository: NotificationRepository,
    ) {
        suspend fun fetchAndSyncYouTubeNotifications(): Result<List<NotificationEntity>> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val jsonResult = YouTube.accountNotifications().getOrThrow()
                    val parsedItems = parseNotificationItems(jsonResult)

                    parsedItems.forEach { entity ->
                        notificationRepository.insertNotification(entity)
                    }

                    parsedItems
                }
            }

        private fun parseNotificationItems(json: JsonElement): List<NotificationEntity> {
            val items = mutableListOf<NotificationEntity>()

            fun extractFromElement(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        val notificationRenderer = element["notificationRenderer"] as? JsonObject
                        if (notificationRenderer != null) {
                            val videoId =
                                notificationRenderer["navigationEndpoint"]
                                    ?.jsonObject
                                    ?.get("watchEndpoint")
                                    ?.jsonObject
                                    ?.get("videoId")
                                    ?.jsonPrimitive
                                    ?.content
                                    .orEmpty()

                            val title =
                                notificationRenderer["shortMessage"]
                                    ?.jsonObject
                                    ?.get("runs")
                                    ?.jsonArray
                                    ?.firstOrNull()
                                    ?.jsonObject
                                    ?.get("text")
                                    ?.jsonPrimitive
                                    ?.content
                                    ?: notificationRenderer["headline"]
                                        ?.jsonObject
                                        ?.get("runs")
                                        ?.jsonArray
                                        ?.firstOrNull()
                                        ?.jsonObject
                                        ?.get("text")
                                        ?.jsonPrimitive
                                        ?.content
                                    ?: "YouTube Notification"

                            val channelName =
                                notificationRenderer["contextualHeader"]
                                    ?.jsonObject
                                    ?.get("runs")
                                    ?.jsonArray
                                    ?.firstOrNull()
                                    ?.jsonObject
                                    ?.get("text")
                                    ?.jsonPrimitive
                                    ?.content ?: "YouTube"

                            val avatarUrl =
                                notificationRenderer["thumbnail"]
                                    ?.jsonObject
                                    ?.get("thumbnails")
                                    ?.jsonArray
                                    ?.firstOrNull()
                                    ?.jsonObject
                                    ?.get("url")
                                    ?.jsonPrimitive
                                    ?.content

                            if (videoId.isNotBlank() || title.isNotBlank()) {
                                items.add(
                                    NotificationEntity(
                                        videoId = videoId,
                                        title = title,
                                        channelName = channelName,
                                        thumbnailUrl = avatarUrl,
                                        timestamp = System.currentTimeMillis(),
                                        isRead = false,
                                        type = "YOUTUBE_ACCOUNT",
                                    ),
                                )
                            }
                        }

                        element.values.forEach(::extractFromElement)
                    }

                    is JsonArray -> {
                        element.forEach(::extractFromElement)
                    }

                    else -> {
                        Unit
                    }
                }
            }

            extractFromElement(json)
            return items.distinctBy { it.videoId + it.title }
        }
    }
