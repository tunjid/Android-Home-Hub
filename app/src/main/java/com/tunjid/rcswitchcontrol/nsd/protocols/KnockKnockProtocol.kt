package com.tunjid.rcswitchcontrol.nsd.protocols

import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Payload
import java.io.PrintWriter

/**
 * Simple communications protoclol for testing, tells knock knock jokes.
 *
 *
 * Created by tj.dahunsi on 2/5/17.
 */

internal class KnockKnockProtocol(printWriter: PrintWriter) : CommsProtocol(printWriter) {


    private var state = WAITING
    private var currentJoke = 0

    private val clues: Array<String> = appContext.resources.getStringArray(R.array.knockknockProtocol_clues)
    private val answers: Array<String> = appContext.resources.getStringArray(R.array.knockknockProtocol_answers)
    private val numJokes: Int = clues.size

    override fun processInput(payload: Payload): Payload {
        val resources = appContext.resources
        val builder = Payload.builder()
        builder.setKey(javaClass.name)
        builder.addCommand(RESET)

        val action = payload.action

        if (action == PING || action == RESET) {
            state = WAITING
            currentJoke = 0
        }

        when (state) {
            WAITING -> {
                builder.setResponse(appContext.getString(R.string.knockknockprotocol_joke_start))
                        .addCommand(appContext.getString(R.string.knockknockprotocol_whos_there))

                state = SENT_KNOCK_KNOCK
            }
            SENT_KNOCK_KNOCK -> state = when {
                action?.trim { it <= ' ' }.equals(appContext.getString(R.string.knockknockprotocol_whos_there), ignoreCase = true) -> {
                    builder.setResponse(clues[currentJoke])
                            .addCommand(resources.getString(R.string.knockknockprotocol_who, clues[currentJoke]))

                     SENT_CLUE
                }
                else -> {
                    val formatString = appContext.getString(R.string.knockknockprotocol_whos_there)
                    val response = resources.getString(R.string.knockknockprotocol_wrong_answer, formatString)
                    builder.setResponse(response)
                            .addCommand(appContext.getString(R.string.knockknockprotocol_whos_there))

                    state
                }
            }
            SENT_CLUE -> state = when {
                action.equals(resources.getString(R.string.knockknockprotocol_who, clues[currentJoke]), ignoreCase = true) -> {
                    builder.setResponse(resources.getString(R.string.knockknockprotocol_want_another, answers[currentJoke]))
                            .addCommand(resources.getString(R.string.knockknockprotocol_no))
                            .addCommand(resources.getString(R.string.knockknockprotocol_yes))

                    ANOTHER
                }
                else -> {
                    val formatString = resources.getString(R.string.knockknockprotocol_who, clues[currentJoke])
                    val response = resources.getString(R.string.knockknockprotocol_wrong_answer, formatString)
                    builder.setResponse(response)
                            .addCommand(appContext.getString(R.string.knockknockprotocol_whos_there))

                    SENT_KNOCK_KNOCK
                }
            }
            ANOTHER -> state = when {
                action.equals(resources.getString(R.string.knockknockprotocol_yes), ignoreCase = true) -> {
                    builder.setResponse(appContext.getString(R.string.knockknockprotocol_joke_start))
                    builder.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there))

                    if (currentJoke == numJokes - 1) currentJoke = 0
                    else currentJoke++

                    SENT_KNOCK_KNOCK
                }
                else -> {
                    builder.setResponse(resources.getString(R.string.commsprotocol_bye))

                    WAITING
                }
            }
        }

        return builder.build()
    }

    override fun close() {
        state = WAITING
    }

    companion object {
        private const val WAITING = 0
        private const val SENT_KNOCK_KNOCK = 1
        private const val SENT_CLUE = 2
        private const val ANOTHER = 3
    }
}
