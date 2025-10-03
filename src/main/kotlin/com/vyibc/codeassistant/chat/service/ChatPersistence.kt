package com.vyibc.codeassistant.chat.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.vyibc.codeassistant.chat.model.ChatMessage
import com.vyibc.codeassistant.chat.model.ChatSession
import com.vyibc.codeassistant.chat.model.MessageType
import com.vyibc.codeassistant.chat.model.SessionCodeSnapshot

@State(name = "CodeChatPersistence", storages = [Storage("CodeChatSessions.xml")])
@Service
class ChatPersistence : PersistentStateComponent<ChatPersistence.State> {

    data class PersistentMessage(
        var type: String = MessageType.USER.name,
        var content: String = "",
        var timestamp: Long = System.currentTimeMillis()
    )

    data class PersistentSession(
        var id: String = java.util.UUID.randomUUID().toString(),
        var className: String = "",
        var filePath: String = "",
        var createdAt: Long = System.currentTimeMillis(),
        var lastActiveAt: Long = System.currentTimeMillis(),
        var messages: MutableList<PersistentMessage> = mutableListOf(),
        var lastSelectedCode: String? = null,
        var lastSelectionStart: Int? = null,
        var lastSelectionEnd: Int? = null,
        var lastMethodName: String? = null
    )

    class State {
        var sessions: MutableList<PersistentSession> = mutableListOf()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun loadSessions(maxMessages: Int): List<ChatSession> {
        return myState.sessions.map { ps ->
            val lastSnapshot = ps.lastSelectedCode?.takeIf { it.isNotBlank() }?.let {
                SessionCodeSnapshot(
                    filePath = ps.filePath,
                    className = ps.className,
                    methodName = ps.lastMethodName,
                    selectedCode = it,
                    selectionStart = ps.lastSelectionStart ?: 0,
                    selectionEnd = ps.lastSelectionEnd ?: 0
                )
            }

            ChatSession(
                id = ps.id,
                className = ps.className,
                filePath = ps.filePath,
                createdAt = ps.createdAt,
                lastActiveAt = ps.lastActiveAt,
                messages = ps.messages.takeLast(maxMessages).map { pm ->
                    ChatMessage(
                        type = runCatching { MessageType.valueOf(pm.type) }.getOrElse { MessageType.USER },
                        content = pm.content,
                        timestamp = pm.timestamp
                    )
                }.toMutableList(),
                lastCodeSnapshot = lastSnapshot
            )
        }
    }

    fun saveOrUpdate(session: ChatSession, maxMessages: Int) {
        val idx = myState.sessions.indexOfFirst { it.id == session.id }
        val target = if (idx >= 0) myState.sessions[idx] else PersistentSession(id = session.id).also { myState.sessions.add(it) }
        target.className = session.className
        target.filePath = session.filePath
        target.createdAt = session.createdAt
        target.lastActiveAt = session.lastActiveAt
        target.messages = session.messages.takeLast(maxMessages).map {
            PersistentMessage(type = it.type.name, content = it.content, timestamp = it.timestamp)
        }.toMutableList()
        session.lastCodeSnapshot?.let { snapshot ->
            target.lastSelectedCode = snapshot.selectedCode
            target.lastSelectionStart = snapshot.selectionStart
            target.lastSelectionEnd = snapshot.selectionEnd
            target.lastMethodName = snapshot.methodName
        } ?: run {
            target.lastSelectedCode = null
            target.lastSelectionStart = null
            target.lastSelectionEnd = null
            target.lastMethodName = null
        }
    }

    fun deleteById(sessionId: String) {
        myState.sessions.removeIf { it.id == sessionId }
    }
}
