package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.AddGroupMembersRequest
import com.umuterayaltay.sosyal.nativeapp.network.ConversationInfoDto
import com.umuterayaltay.sosyal.nativeapp.network.CreateGroupRequest
import com.umuterayaltay.sosyal.nativeapp.network.GroupMemberDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import com.umuterayaltay.sosyal.nativeapp.network.MessagingApi
import com.umuterayaltay.sosyal.nativeapp.network.RenameGroupRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.ConversationSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

// ---- Grup yönetimi (Faz 4) — her endpoint için AYRI, dar bir sonuç tipi
// (ConversationsResult/SendMessageResult/StartConversationResult ile AYNI
// granülerlik geleneği; MarkReadResult'ın ok/error şekli REUSE edilmedi çünkü
// isim anlam taşımıyor olurdu, her endpoint kendi adıyla tanımlandı).

sealed class CreateGroupResult {
    data class Success(val conversationId: String) : CreateGroupResult()
    data class Error(val code: String?) : CreateGroupResult()
}

sealed class RenameGroupResult {
    data class Success(val name: String) : RenameGroupResult()
    data class Error(val code: String?) : RenameGroupResult()
}

sealed class GroupMembersResult {
    data class Success(val members: List<GroupMemberDto>) : GroupMembersResult()
    data class Error(val code: String?) : GroupMembersResult()
}

sealed class AddGroupMembersResult {
    data class Success(val added: List<GroupMemberDto>) : AddGroupMembersResult()
    data class Error(val code: String?) : AddGroupMembersResult()
}

sealed class RemoveGroupMemberResult {
    data object Success : RemoveGroupMemberResult()
    data class Error(val code: String?) : RemoveGroupMemberResult()
}

sealed class ToggleAdminResult {
    data class Success(val isAdmin: Boolean) : ToggleAdminResult()
    data class Error(val code: String?) : ToggleAdminResult()
}

sealed class LeaveGroupResult {
    data object Success : LeaveGroupResult()
    data class Error(val code: String?) : LeaveGroupResult()
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

    /**
     * Metin ve/veya görsel gönderir — app/api_v1.py api_send_message() artık
     * multipart/form-data bekliyor (JSON DEĞİL). En az content VEYA imageBytes
     * dolu olmalı (backend ikisi de boşsa "empty" döner) — burada ayrıca bir
     * ön-kontrol YAPILMIYOR, tek doğruluk kaynağı backend olsun diye
     * (ConversationViewModel.send() UI tarafında aynı kontrolü zaten yapıyor,
     * InteractionsRepository.createPost() ile AYNI gerekçe). replyToId null ise
     * @Part de null geçilir (Retrofit null Part'ı atlar).
     */
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        replyToId: String?,
        imageBytes: ByteArray?,
        imageMimeType: String?,
    ): SendMessageResult =
        withContext(Dispatchers.IO) {
            try {
                val contentBody: RequestBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
                val replyToBody: RequestBody? = replyToId?.toRequestBody("text/plain".toMediaTypeOrNull())
                val imagePart: MultipartBody.Part? = imageBytes?.let { bytes ->
                    val imageBody = bytes.toRequestBody((imageMimeType ?: "image/jpeg").toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("image", "message_image", imageBody)
                }

                val response = messagingApi.sendMessage(
                    conversationId,
                    contentBody,
                    replyToBody,
                    imagePart,
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

    // ---- Grup yönetimi (Faz 4) ----

    suspend fun createGroup(name: String, userIds: List<String>): CreateGroupResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.createGroup(CreateGroupRequest(name = name, userIds = userIds))
            val body = response.body()
            if (response.isSuccessful && body?.conversationId != null) {
                CreateGroupResult.Success(body.conversationId)
            } else {
                CreateGroupResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            CreateGroupResult.Error("network_error")
        } catch (e: Exception) {
            CreateGroupResult.Error("unknown_error")
        }
    }

    suspend fun renameGroup(conversationId: String, name: String): RenameGroupResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.renameGroup(conversationId, RenameGroupRequest(name = name))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                RenameGroupResult.Success(body.name ?: name)
            } else {
                RenameGroupResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            RenameGroupResult.Error("network_error")
        } catch (e: Exception) {
            RenameGroupResult.Error("unknown_error")
        }
    }

    suspend fun getGroupMembers(conversationId: String): GroupMembersResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.getGroupMembers(conversationId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                GroupMembersResult.Success(body.members ?: emptyList())
            } else {
                GroupMembersResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            GroupMembersResult.Error("network_error")
        } catch (e: Exception) {
            GroupMembersResult.Error("unknown_error")
        }
    }

    suspend fun addGroupMembers(conversationId: String, userIds: List<String>): AddGroupMembersResult =
        withContext(Dispatchers.IO) {
            try {
                val response = messagingApi.addGroupMembers(conversationId, AddGroupMembersRequest(userIds = userIds))
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    AddGroupMembersResult.Success(body.added ?: emptyList())
                } else {
                    AddGroupMembersResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                AddGroupMembersResult.Error("network_error")
            } catch (e: Exception) {
                AddGroupMembersResult.Error("unknown_error")
            }
        }

    suspend fun removeGroupMember(conversationId: String, userId: String): RemoveGroupMemberResult =
        withContext(Dispatchers.IO) {
            try {
                val response = messagingApi.removeGroupMember(conversationId, userId)
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    RemoveGroupMemberResult.Success
                } else {
                    RemoveGroupMemberResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                RemoveGroupMemberResult.Error("network_error")
            } catch (e: Exception) {
                RemoveGroupMemberResult.Error("unknown_error")
            }
        }

    suspend fun toggleGroupAdmin(conversationId: String, userId: String): ToggleAdminResult =
        withContext(Dispatchers.IO) {
            try {
                val response = messagingApi.toggleGroupAdmin(conversationId, userId)
                val body = response.body()
                if (response.isSuccessful && body?.ok == true && body.isAdmin != null) {
                    ToggleAdminResult.Success(body.isAdmin)
                } else {
                    ToggleAdminResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                ToggleAdminResult.Error("network_error")
            } catch (e: Exception) {
                ToggleAdminResult.Error("unknown_error")
            }
        }

    suspend fun leaveGroup(conversationId: String): LeaveGroupResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.leaveGroup(conversationId)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                LeaveGroupResult.Success
            } else {
                LeaveGroupResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            LeaveGroupResult.Error("network_error")
        } catch (e: Exception) {
            LeaveGroupResult.Error("unknown_error")
        }
    }
}
