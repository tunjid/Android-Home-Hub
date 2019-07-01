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
        val output = Payload(javaClass.name)
        output.addCommand(RESET)

        val action = payload.action

        if (action == PING || action == RESET) {
            state = WAITING
            currentJoke = 0
        }

        when (state) {
            WAITING -> {
                output.response = appContext.getString(R.string.knockknockprotocol_joke_start)
                output.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there))

                state = SENT_KNOCK_KNOCK
            }
            SENT_KNOCK_KNOCK -> state = when {
                action?.trim { it <= ' ' }.equals(appContext.getString(R.string.knockknockprotocol_whos_there), ignoreCase = true) -> {
                    output.response = clues[currentJoke]
                    output.addCommand(resources.getString(R.string.knockknockprotocol_who, clues[currentJoke]))

                    SENT_CLUE
                }
                else -> {
                    val formatString = appContext.getString(R.string.knockknockprotocol_whos_there)
                    val response = resources.getString(R.string.knockknockprotocol_wrong_answer, formatString)
                    output.response = response
                    output.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there))

                    state
                }
            }
            SENT_CLUE -> state = when {
                action.equals(resources.getString(R.string.knockknockprotocol_who, clues[currentJoke]), ignoreCase = true) -> {
                    output.response = resources.getString(R.string.knockknockprotocol_want_another, answers[currentJoke])
                    output.addCommand(resources.getString(R.string.knockknockprotocol_no))
                    output.addCommand(resources.getString(R.string.knockknockprotocol_yes))

                    ANOTHER
                }
                else -> {
                    val formatString = resources.getString(R.string.knockknockprotocol_who, clues[currentJoke])
                    val response = resources.getString(R.string.knockknockprotocol_wrong_answer, formatString)
                    output.response = response
                    output.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there))

                    SENT_KNOCK_KNOCK
                }
            }
            ANOTHER -> state = when {
                action.equals(resources.getString(R.string.knockknockprotocol_yes), ignoreCase = true) -> {
                    output.response = appContext.getString(R.string.knockknockprotocol_joke_start)
                    output.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there))

                    if (currentJoke == numJokes - 1) currentJoke = 0
                    else currentJoke++

                    SENT_KNOCK_KNOCK
                }
                else -> {
                    output.response = resources.getString(R.string.commsprotocol_bye)

                    WAITING
                }
            }
        }

        return output
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
