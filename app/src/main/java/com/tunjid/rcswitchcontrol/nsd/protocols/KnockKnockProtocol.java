package com.tunjid.rcswitchcontrol.nsd.protocols;

import android.content.res.Resources;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.Payload;

/**
 * Simple communications protoclol for testing, tells knock knock jokes.
 * <p>
 * Created by tj.dahunsi on 2/5/17.
 */

class KnockKnockProtocol extends CommsProtocol {
    private static final int WAITING = 0;
    private static final int SENT_KNOCK_KNOCK = 1;
    private static final int SENT_CLUE = 2;
    private static final int ANOTHER = 3;

    private final int numJokes;

    private int state = WAITING;
    private int currentJoke = 0;

    private final String[] clues;
    private final String[] answers;

    KnockKnockProtocol() {
        super();
        clues = appContext.getResources().getStringArray(R.array.knockknockProtocol_clues);
        answers = appContext.getResources().getStringArray(R.array.knockknockProtocol_answers);
        numJokes = clues.length;
    }

    @Override
    public Payload processInput(Payload input) {
        Resources resources = appContext.getResources();
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName());
        builder.addCommand(RESET);

        String action = input.getAction();

        if (action.equals(PING) || action.equals(RESET)) {
            state = WAITING;
            currentJoke = 0;
        }

        if (state == WAITING) {
            builder.setResponse(appContext.getString(R.string.knockknockprotocol_joke_start));
            builder.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there));
            state = SENT_KNOCK_KNOCK;
        }
        else if (state == SENT_KNOCK_KNOCK) {
            if (action.trim().equalsIgnoreCase(appContext.getString(R.string.knockknockprotocol_whos_there))) {
                builder.setResponse(clues[currentJoke]);
                builder.addCommand(resources.getString(R.string.knockknockprotocol_who, clues[currentJoke]));
                state = SENT_CLUE;
            }
            else {
                String formatString = appContext.getString(R.string.knockknockprotocol_whos_there);
                String response = resources.getString(R.string.knockknockprotocol_wrong_answer, formatString);
                builder.setResponse(response);
                builder.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there));
            }
        }
        else if (state == SENT_CLUE) {
            if (action.equalsIgnoreCase(resources.getString(R.string.knockknockprotocol_who, clues[currentJoke]))) {
                builder.setResponse(resources.getString(R.string.knockknockprotocol_want_another, answers[currentJoke]))
                        .addCommand(resources.getString(R.string.knockknockprotocol_no))
                        .addCommand(resources.getString(R.string.knockknockprotocol_yes));
                state = ANOTHER;
            }
            else {
                String formatString = resources.getString(R.string.knockknockprotocol_who, clues[currentJoke]);
                String response = resources.getString(R.string.knockknockprotocol_wrong_answer, formatString);
                builder.setResponse(response);
                builder.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there));
                state = SENT_KNOCK_KNOCK;
            }
        }
        else if (state == ANOTHER) {
            if (action.equalsIgnoreCase(resources.getString(R.string.knockknockprotocol_yes))) {
                builder.setResponse(appContext.getString(R.string.knockknockprotocol_joke_start));
                builder.addCommand(appContext.getString(R.string.knockknockprotocol_whos_there));

                if (currentJoke == (numJokes - 1)) currentJoke = 0;
                else currentJoke++;
                state = SENT_KNOCK_KNOCK;
            }
            else {
                builder.setResponse(resources.getString(R.string.commsprotocol_bye));
                state = WAITING;
            }
        }
        return builder.build();
    }

    @Override
    public void close() {
        state = WAITING;
    }
}
