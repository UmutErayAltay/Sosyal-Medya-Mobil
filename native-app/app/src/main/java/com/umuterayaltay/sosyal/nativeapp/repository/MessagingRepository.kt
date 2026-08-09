package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.AddGroupMembersRequest
import com.umuterayaltay.sosyal.nativeapp.network.ConversationInfoDto
import com.umuterayaltay.sosyal.nativeapp.network.CreateGroupRequest
import com.umuterayaltay.sosyal.nativeapp.network.EditMessageRequest
import com.umuterayaltay.sosyal.nativeapp.network.ForwardMessageRequest
import com.umuterayaltay.sosyal.nativeapp.network.ForwardTargetDto
import com.umuterayaltay.sosyal.nativeapp.network.GroupMemberDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageSearchResultDto
import com.umuterayaltay.sosyal.nativeapp.network.MessagingApi
import com.umuterayaltay.sosyal.nativeapp.network.ReactRequest
import com.umuterayaltay.sosyal.nativeapp.network.RenameGroupRequest
import com.umuterayaltay.sosyal.nativeapp.network.RingCallRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.ConversationSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.PeerConnection
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

// ---- Mesaj gelişmiş işlemleri (Faz 5 Dalga 1B) — her endpoint için AYRI, dar
// bir sonuç tipi (yukarıdaki grup yönetimi sonuçlarıyla AYNI granülerlik
// geleneği). Sessize alma (mute) da AYNI dalgada (backend'de aynı dosyada)
// olduğu için burada.

sealed class DeleteMessageResult {
    data object Success : DeleteMessageResult()
    data class Error(val code: String?) : DeleteMessageResult()
}

sealed class EditMessageResult {
    data class Success(val content: String, val editedAt: String?) : EditMessageResult()
    data class Error(val code: String?) : EditMessageResult()
}

/** [reaction] null ise tepki KALDIRILDI (3'lü toggle'ın "sil" dalı) — bkz.
 * ApiModels.kt ReactResponse yorumu. */
sealed class ReactToMessageResult {
    data class Success(val reaction: String?) : ReactToMessageResult()
    data class Error(val code: String?) : ReactToMessageResult()
}

sealed class PinMessageResult {
    data class Success(val pinned: Boolean) : PinMessageResult()
    data class Error(val code: String?) : PinMessageResult()
}

sealed class MuteConversationResult {
    data class Success(val muted: Boolean) : MuteConversationResult()
    data class Error(val code: String?) : MuteConversationResult()
}

sealed class ForwardTargetsResult {
    data class Success(val targets: List<ForwardTargetDto>) : ForwardTargetsResult()
    data class Error(val code: String?) : ForwardTargetsResult()
}

sealed class ForwardMessageResult {
    data class Success(val conversationId: String) : ForwardMessageResult()
    data class Error(val code: String?) : ForwardMessageResult()
}

/** Sohbet-içi VE global arama AYNI sonuç tipini paylaşır — ikisi de AYNI
 * şekli (results/hasNext/nextOffset) döner, ayrı bir tip İCAT EDİLMEDİ. */
sealed class MessageSearchResult {
    data class Success(
        val results: List<MessageSearchResultDto>,
        val hasNext: Boolean,
        val nextOffset: Int?,
    ) : MessageSearchResult()
    data class Error(val code: String?) : MessageSearchResult()
}

/** Grup sesli/görüntülü arama (native görev — LiveKit) — app/api_v1/messaging.py
 * api_call_token() ile birebir eşleşir. [Error.code] backend'in ürettiği
 * ham hata koduyla AYNI ("group_calls_not_configured"/"not_found"/
 * "unauthorized"/"token_generation_failed") — CallViewModel bu kodlara göre
 * dallanır (bkz. o dosyanın handleError() yorumu), burada AYRICA bir eşleme
 * YAPILMAZ (diğer *Result.Error desenleriyle AYNI, tek doğruluk kaynağı backend). */
sealed class CallTokenResult {
    data class Success(val token: String, val url: String, val room: String) : CallTokenResult()
    data class Error(val code: String?) : CallTokenResult()
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
     * Metin ve/veya görsel/sticker/GIF gönderir — app/api_v1.py api_send_message()
     * multipart/form-data bekliyor (JSON DEĞİL). En az content VEYA imageBytes
     * VEYA stickerId VEYA gifUrl dolu olmalı (backend hepsi boşsa "empty"
     * döner) — burada ayrıca bir ön-kontrol YAPILMIYOR, tek doğruluk kaynağı
     * backend olsun diye (ConversationViewModel.send() UI tarafında aynı
     * kontrolü zaten yapıyor, InteractionsRepository.createPost() ile AYNI
     * gerekçe). Tüm opsiyonel parametreler null ise @Part de null geçilir
     * (Retrofit null Part'ı isteğe hiç eklemez).
     */
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        replyToId: String?,
        imageBytes: ByteArray?,
        imageMimeType: String?,
        stickerId: String? = null,
        gifUrl: String? = null,
        // 2026-08-08: video gönderme — imageBytes ile AYNI desen, ayrı bir
        // "video" multipart alanı (backend upload_video()'ya gider).
        videoBytes: ByteArray? = null,
        videoMimeType: String? = null,
        // 2026-08-08 (kullanıcı raporu: "gidiyor ama hemen geri geliyor,
        // göndermiyor") — dosya adı ARTIK sabit "message_image"/"message_video"
        // DEĞİL, çağıran taraf (ConversationViewModel) gerçek MIME'dan gerçek
        // uzantılı bir isim üretip buraya veriyor. Uzantısız isim backend'in
        // `_get_extension()` kontrolünü HER ZAMAN başarısız kılıyordu (bkz.
        // ConversationViewModel'deki kök neden yorumu) — fallback DEĞERLERİ
        // ("upload.jpg"/"upload.mp4") sadece çağıran taraf her nedense null
        // geçerse diye son bir güvenlik ağı, normal akışta HİÇ kullanılmaz.
        imageFileName: String? = null,
        videoFileName: String? = null,
        // 2026-08-09: sesli mesaj — image/videoBytes ile AYNI desen, backend'in
        // YENİ "audio" multipart alanına gider (bkz. app/api_v1/messaging.py
        // api_send_message() — form alanı adı `audio`, izin verilen uzantı/MIME
        // SABİT: .webm/.ogg/.mp3/.m4a/.wav, audio/webm/video/webm/audio/ogg/
        // audio/mpeg/audio/mp4/audio/wav/audio/x-wav, 10MB üst sınır — bkz.
        // app/storage_helper.py ALLOWED_AUDIO_*). ConversationViewModel'in
        // MediaRecorder çıktısı `.m4a`/`audio/mp4` kullanır (`audio/m4a` bu
        // listede YOK).
        audioBytes: ByteArray? = null,
        audioMimeType: String? = null,
        audioFileName: String? = null,
    ): SendMessageResult =
        withContext(Dispatchers.IO) {
            try {
                val contentBody: RequestBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
                val replyToBody: RequestBody? = replyToId?.toRequestBody("text/plain".toMediaTypeOrNull())
                val imagePart: MultipartBody.Part? = imageBytes?.let { bytes ->
                    val imageBody = bytes.toRequestBody((imageMimeType ?: "image/jpeg").toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("image", imageFileName ?: "upload.jpg", imageBody)
                }
                val videoPart: MultipartBody.Part? = videoBytes?.let { bytes ->
                    val videoBody = bytes.toRequestBody((videoMimeType ?: "video/mp4").toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("video", videoFileName ?: "upload.mp4", videoBody)
                }
                val audioPart: MultipartBody.Part? = audioBytes?.let { bytes ->
                    val audioBody = bytes.toRequestBody((audioMimeType ?: "audio/mp4").toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("audio", audioFileName ?: "upload.m4a", audioBody)
                }
                val stickerIdBody: RequestBody? = stickerId?.toRequestBody("text/plain".toMediaTypeOrNull())
                val gifUrlBody: RequestBody? = gifUrl?.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = messagingApi.sendMessage(
                    conversationId,
                    contentBody,
                    replyToBody,
                    imagePart,
                    videoPart,
                    audioPart,
                    stickerIdBody,
                    gifUrlBody,
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

    // ---- Mesaj gelişmiş işlemleri (Faz 5 Dalga 1B) ----

    /** Backend HER ZAMAN {ok:true} döner (hiçbir satır eşleşmese bile — bkz.
     * api_delete_message() docstring'i, "bu id'li mesaj var mı" sorusuna cevap
     * sızdırmama amacı da var), bu yüzden pratikte Error yalnızca ağ/HTTP
     * seviyesinde oluşur. */
    suspend fun deleteMessage(messageId: String): DeleteMessageResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.deleteMessage(messageId)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                DeleteMessageResult.Success
            } else {
                DeleteMessageResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            DeleteMessageResult.Error("network_error")
        } catch (e: Exception) {
            DeleteMessageResult.Error("unknown_error")
        }
    }

    suspend fun editMessage(messageId: String, content: String): EditMessageResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.editMessage(messageId, EditMessageRequest(content = content))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true && body.content != null) {
                EditMessageResult.Success(body.content, body.editedAt)
            } else {
                EditMessageResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            EditMessageResult.Error("network_error")
        } catch (e: Exception) {
            EditMessageResult.Error("unknown_error")
        }
    }

    /** ok=true iken reaction null OLABİLİR (tepki kaldırıldı) — bu yüzden
     * başarı kontrolü body.reaction'ın doluluğuna değil SADECE body.ok'a
     * bakar (ForwardMessageResult/PinMessageResult'taki AYNI desen). */
    suspend fun reactToMessage(messageId: String, reaction: String): ReactToMessageResult =
        withContext(Dispatchers.IO) {
            try {
                val response = messagingApi.reactToMessage(messageId, ReactRequest(reaction = reaction))
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    ReactToMessageResult.Success(body.reaction)
                } else {
                    ReactToMessageResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                ReactToMessageResult.Error("network_error")
            } catch (e: Exception) {
                ReactToMessageResult.Error("unknown_error")
            }
        }

    suspend fun pinMessage(messageId: String): PinMessageResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.pinMessage(messageId)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                PinMessageResult.Success(body.pinned)
            } else {
                PinMessageResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            PinMessageResult.Error("network_error")
        } catch (e: Exception) {
            PinMessageResult.Error("unknown_error")
        }
    }

    suspend fun getForwardTargets(): ForwardTargetsResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.getForwardTargets()
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                ForwardTargetsResult.Success(body.targets ?: emptyList())
            } else {
                ForwardTargetsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ForwardTargetsResult.Error("network_error")
        } catch (e: Exception) {
            ForwardTargetsResult.Error("unknown_error")
        }
    }

    suspend fun forwardMessage(messageId: String, targetConversationId: String): ForwardMessageResult =
        withContext(Dispatchers.IO) {
            try {
                val response = messagingApi.forwardMessage(
                    messageId,
                    ForwardMessageRequest(targetConversationId = targetConversationId),
                )
                val body = response.body()
                if (response.isSuccessful && body?.ok == true && body.conversationId != null) {
                    ForwardMessageResult.Success(body.conversationId)
                } else {
                    ForwardMessageResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                ForwardMessageResult.Error("network_error")
            } catch (e: Exception) {
                ForwardMessageResult.Error("unknown_error")
            }
        }

    suspend fun muteConversation(conversationId: String): MuteConversationResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.muteConversation(conversationId)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                MuteConversationResult.Success(body.muted)
            } else {
                MuteConversationResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            MuteConversationResult.Error("network_error")
        } catch (e: Exception) {
            MuteConversationResult.Error("unknown_error")
        }
    }

    suspend fun searchInConversation(conversationId: String, query: String, offset: Int): MessageSearchResult =
        withContext(Dispatchers.IO) {
            try {
                val response = messagingApi.searchInConversation(conversationId, query, offset)
                val body = response.body()
                if (response.isSuccessful && body != null && body.error == null) {
                    MessageSearchResult.Success(body.results ?: emptyList(), body.hasNext, body.nextOffset)
                } else {
                    MessageSearchResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                MessageSearchResult.Error("network_error")
            } catch (e: Exception) {
                MessageSearchResult.Error("unknown_error")
            }
        }

    suspend fun searchAllMessages(query: String, offset: Int): MessageSearchResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.searchAllMessages(query, offset)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                MessageSearchResult.Success(body.results ?: emptyList(), body.hasNext, body.nextOffset)
            } else {
                MessageSearchResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            MessageSearchResult.Error("network_error")
        } catch (e: Exception) {
            MessageSearchResult.Error("unknown_error")
        }
    }

    // ---- Grup sesli/görüntülü arama (native görev — LiveKit) ----

    suspend fun getCallToken(conversationId: String): CallTokenResult = withContext(Dispatchers.IO) {
        try {
            val response = messagingApi.getCallToken(conversationId)
            val body = response.body()
            if (response.isSuccessful && body?.token != null && body.url != null && body.room != null) {
                CallTokenResult.Success(body.token, body.url, body.room)
            } else {
                CallTokenResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            CallTokenResult.Error("network_error")
        } catch (e: Exception) {
            CallTokenResult.Error("unknown_error")
        }
    }

    /** 1:1 arama başlatılınca hedefi FCM ile uyandırır (2026-08-08) — bkz.
     * RingCallRequest/app/fcm.py::send_call_wake_fcm() yorumu. BİLEREK
     * best-effort: dönen sonuç arayan tarafın kendi arama akışını (Realtime
     * offer gönderimi ZATEN ayrı/asıl yol) hiçbir şekilde ENGELLEMEMELİ —
     * CallSessionManager çağrı yerinde sonucu KONTROL ETMEZ. */
    suspend fun ringCall(targetUserId: String) {
        withContext(Dispatchers.IO) {
            try {
                messagingApi.ringCall(RingCallRequest(targetUserId))
            } catch (e: Exception) {
                // best-effort — arama zaten Realtime broadcast ile devam ediyor.
            }
        }
    }

    /** WebRTC TURN/STUN sunucu listesini backend'den ANLIK çeker (2026-08-09)
     * — eski SABİT/paylaşımlı openrelayproject kimlik bilgisi yerine, backend'in
     * kendi metered.ca API key'iyle ürettiği kısa-ömürlü liste (bkz. IceServerDto
     * yorumu, web'in call.js#getIceServers() ile AYNI mantık). Ağ hatası/boş
     * yanıtta SADECE Google'ın herkese açık STUN sunucusunu içeren bir fallback
     * döner — TAMAMEN başarısız OLMAZ, aynı ağdaki aramalar yine çalışabilir. */
    suspend fun getIceServers(): List<PeerConnection.IceServer> = withContext(Dispatchers.IO) {
        val fallback = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        try {
            val resp = messagingApi.getTurnCredentials()
            val servers = if (resp.isSuccessful) resp.body() else null
            if (servers.isNullOrEmpty()) return@withContext fallback
            servers.mapNotNull { dto ->
                val urls = dto.urls ?: return@mapNotNull null
                val builder = PeerConnection.IceServer.builder(urls)
                if (dto.username != null) builder.setUsername(dto.username)
                if (dto.credential != null) builder.setPassword(dto.credential)
                builder.createIceServer()
            }.ifEmpty { fallback }
        } catch (e: Exception) {
            fallback
        }
    }
}
