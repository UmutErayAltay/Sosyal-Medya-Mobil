package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.ConversationInfoDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import com.umuterayaltay.sosyal.nativeapp.network.MessagingApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SendMessageRequest
import com.umuterayaltay.sosyal.nativeapp.network.ConversationSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class ConversationsResult {
    data class Success(val conversations: List<ConversationSummaryDto>) : ConversationsResult()
    data class Error(val code: String?) : ConversationsResult()
}

sealed class ConversationDetailResult {
    data class Success(
        val messages: List<MessageDto>,
        val hasMore: Boolean,
        val conversation: ConversationInfoDto?,
    ) : ConversationDetailResult()
    data class Error(val code: String?) : ConversationDetailResult()
}

sealed class SendMessageResult {
    data class Success(val message: MessageDto) : SendMessageResult()
    data class Error(val code: String?) : SendMessageResult()
}

sealed class StartConversationResult {
    data class Success(val conversationId: String) : StartConversationResult()
    data class Error(val code: String?) : StartConversationResult()
}

/** mark-read gibi ok/error şekilli ikincil eylem — DiscoverRepository.SearchActionResult/
 * ProfileRepository.FollowActionResult ile AYNI desen. */
sealed class MarkReadResult {
    data object Success : MarkReadResult()
    data class Error(val code: String?) : MarkReadResult()
}

/**
 * Gelen kutusu + konuşma geçmişi (sayfalı) + metin mesajı gönder(+reply_to) +
 * yeni konuşma başlat (get-or-create) + okundu işaretle için repository.
 * DiscoverRepository/ProfileRepository ile AYNI gerekçeyle Room cache YOK
 * (canlı veri; ayrıca basit polling zaten "neredeyse canlı" tutuyor — bkz.
 * ConversationViewModel).
 */
class MessagingRepository(
    private val messagingApi: MessagingApi,
) {

    suspend fun getConversations(): ConversationsResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.getConversations()
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                ConversationsResult.Success(body.conversations ?: emptyList())
            } else {
                ConversationsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ConversationsResult.Error("network_error")
        } catch (e: Exception) {
            ConversationsResult.Error("unknown_error")
        }
    }

    suspend fun getConversationDetail(conversationId: String, page: Int): ConversationDetailResult =
        withContext(Dispatchers.IO) {
            try {
                val response = messagingApi.getConversationDetail(conversationId, page)
                val body = response.body()
                if (response.isSuccessful && body != null && body.error == null) {
                    ConversationDetailResult.Success(
                        messages = body.messages ?: emptyList(),
                        hasMore = body.hasMore,
                        conversation = body.conversation,
                    )
                } else {
                    ConversationDetailResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                ConversationDetailResult.Error("network_error")
            } catch (e: Exception) {
                ConversationDetailResult.Error("unknown_error")
            }
        }

    suspend fun sendMessage(conversationId: String, content: String, replyToId: String?): SendMessageResult =
        withContext(Dispatchers.IO) {
            try {
                val response = messagingApi.sendMessage(
                    conversationId,
                    SendMessageRequest(content = content, replyToId = replyToId),
                )
                val body = response.body()
                if (response.isSuccessful && body?.message != null) {
                    SendMessageResult.Success(body.message)
                } else {
                    SendMessageResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                SendMessageResult.Error("network_error")
            } catch (e: Exception) {
                SendMessageResult.Error("unknown_error")
            }
        }

    suspend fun startConversation(username: String): StartConversationResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.startConversation(username)
            val body = response.body()
            if (response.isSuccessful && body?.conversationId != null) {
                StartConversationResult.Success(body.conversationId)
            } else {
                StartConversationResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            StartConversationResult.Error("network_error")
        } catch (e: Exception) {
            StartConversationResult.Error("unknown_error")
        }
    }

    suspend fun markRead(conversationId: String): MarkReadResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.markRead(conversationId)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                MarkReadResult.Success
            } else {
                MarkReadResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            MarkReadResult.Error("network_error")
        } catch (e: Exception) {
            MarkReadResult.Error("unknown_error")
        }
    }
}
