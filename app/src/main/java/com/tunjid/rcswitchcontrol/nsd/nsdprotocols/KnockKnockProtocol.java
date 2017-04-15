package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import com.tunjid.rcswitchcontrol.model.Payload;

import java.io.IOException;

/**
 * Simple communications protoclol for testing, tells knock knock jokes.
 * <p>
 * Created by tj.dahunsi on 2/5/17.
 */

class KnockKnockProtocol extends CommsProtocol {
    private static final int WAITING = 0;
    private static final int SENTKNOCKKNOCK = 1;
    private static final int SENTCLUE = 2;
    private static final int ANOTHER = 3;

    private static final int NUMJOKES = 5;

    private int state = WAITING;
    private int currentJoke = 0;

    private String[] clues = {"Turnip", "Little Old Lady", "Atch", "Who", "Who"};
    private String[] answers = {"Turnip the heat, it's cold in here!",
            "I didn't know you could yodel!",
            "Bless you!",
            "Is there an owl in here?",
            "Is there an echo in here?"};

    @Override
    public Payload processInput(Payload input) {
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName());
        builder.addCommand(RESET);

        String action = input.getAction();

        if (action.equals(PING) || action.equals(RESET)) {
            state = WAITING;
            currentJoke = 0;
        }

        if (state == WAITING) {
            builder.setResponse("Knock! Knock!");
            builder.addCommand("Who's there?");
            state = SENTKNOCKKNOCK;
        }
        else if (state == SENTKNOCKKNOCK) {
            if (action.trim().equalsIgnoreCase("Who's there?")) {
                builder.setResponse(clues[currentJoke]);
                builder.addCommand(clues[currentJoke] + " who?");
                state = SENTCLUE;
            }
            else {
                builder.setResponse("You're supposed to say \"Who's there?\"! " +
                        "Try again. Knock! Knock!");
                builder.addCommand("Who's there?");
            }
        }
        else if (state == SENTCLUE) {
            if (action.equalsIgnoreCase(clues[currentJoke] + " who?")) {
                builder.setResponse(answers[currentJoke] + " Want another? (y/n)");
                builder.addCommand("y");
                builder.addCommand("n");
                state = ANOTHER;
            }
            else {
                builder.setResponse("You're supposed to say \"" +
                        clues[currentJoke] +
                        " who?\"" +
                        "! Try again. Knock! Knock!");
                builder.addCommand("Who's there?");
                state = SENTKNOCKKNOCK;
            }
        }
        else if (state == ANOTHER) {
            if (action.equalsIgnoreCase("y")) {
                builder.setResponse("Knock! Knock!");
                builder.addCommand("Who's there?");

                if (currentJoke == (NUMJOKES - 1)) currentJoke = 0;
                else currentJoke++;
                state = SENTKNOCKKNOCK;
            }
            else {
                builder.setResponse("Bye.");
                state = WAITING;
            }
        }
        return builder.build();
    }

    @Override
    public void close() throws IOException {
        state = WAITING;
    }
}
