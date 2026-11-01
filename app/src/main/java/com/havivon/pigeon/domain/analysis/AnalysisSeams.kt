package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.repository.BlockedKeywordsRepository
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.domain.seam.IncomingVerdict
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import kotlinx.coroutines.flow.first
import com.havivon.pigeon.domain.seam.FolderClassifier as FolderSeam

/** The analysis behind the platform's seams (see `domain/seam`), passed to `AppContainer` by `PigeonApp`. */
object AnalysisSeams {

    val codeFinder: CodeFinder = CodeFinder { CodeExtractor.extract(it) }

    /**
     * The inbox folder of a conversation, from its last message. Spam is not decided here: it comes
     * from the verdict stored when the message arrived (so "Not spam" sticks), and a conversation
     * that fits no automatic folder is [Folder.All], shown only under All.
     */
    val folderClassifier: FolderSeam = FolderSeam { c -> classifyFolder(c) }

    fun classifyFolder(c: SmsConversation): Folder {
        val sms = IncomingSms(
            address = c.addresses.firstOrNull().orEmpty(),
            senderName = c.contactName,
            body = c.snippet,
            isContact = c.contactName != null,
        )
        return FolderClassifier.classify(sms, spam = NOT_SPAM)
    }

    private val NOT_SPAM = SpamVerdict(0, emptyList())
}

/**
 * Scores each new message for spam, with the sender's contact status, the user's "not spam"
 * senders and blocked words. A spam verdict is stored (the conversation moves to the Spam folder)
 * and the message is not notified. It runs only after the message is stored, so a failure here
 * never loses it (the handler then notifies as usual).
 */
class SpamFilterHook(
    private val contacts: ContactsRepository,
    private val spam: SpamRepository,
    private val blockedKeywords: BlockedKeywordsRepository,
) : IncomingMessageHook {

    override suspend fun onStored(message: StoredIncomingSms): IncomingVerdict {
        val name = contacts.displayName(message.address)
        val verdict = SpamScorer.score(
            IncomingSms(
                address = message.address,
                senderName = name,
                body = message.body,
                isContact = name != null,
                allowedSenders = spam.notSpam.first(),
                blockedKeywords = blockedKeywords.keywords.first().map { it.word },
            ),
        )
        if (!verdict.isSpam) return IncomingVerdict.Notify
        // False for a sender on the "not spam" list: then it is not spam after all
        val stored = spam.markSpam(message.address, message.threadId, verdict.reasons)
        return if (stored) IncomingVerdict.Silent else IncomingVerdict.Notify
    }
}
