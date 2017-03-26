package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import java.io.IOException;

/**
 * Simple communications protoclol for testing, tells knock knock jokes.
 * <p>
 * Created by tj.dahunsi on 2/5/17.
 */

class KnockKnockProtocol implements CommsProtocol {
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
    public Payload processInput(String input) {
        Payload output = new Payload();

        if (state == WAITING) {
            output.response = "Knock! Knock!";
            output.commands.add("Who's there?");
            state = SENTKNOCKKNOCK;
        }
        else if (state == SENTKNOCKKNOCK) {
            if (input.trim().equalsIgnoreCase("Who's there?")) {
                output.response = clues[currentJoke];
                output.commands.add(output.response + " who?");
                state = SENTCLUE;
            }
            else {
                output.response = "You're supposed to say \"Who's there?\"! " +
                        "Try again. Knock! Knock!";
                output.commands.add("Who's there?");
            }
        }
        else if (state == SENTCLUE) {
            if (input.equalsIgnoreCase(clues[currentJoke] + " who?")) {
                output.response = answers[currentJoke] + " Want another? (y/n)";
                output.commands.add("y");
                output.commands.add("n");
                state = ANOTHER;
            }
            else {
                output.response = "You're supposed to say \"" +
                        clues[currentJoke] +
                        " who?\"" +
                        "! Try again. Knock! Knock!";
                output.commands.add("Who's there?");
                state = SENTKNOCKKNOCK;
            }
        }
        else if (state == ANOTHER) {
            if (input.equalsIgnoreCase("y")) {
                output.response = "Knock! Knock!";
                output.commands.add("Who's there?");

                if (currentJoke == (NUMJOKES - 1)) currentJoke = 0;
                else currentJoke++;
                state = SENTKNOCKKNOCK;
            }
            else {
                output.response = "Bye.";
                state = WAITING;
            }
        }
        return output;
    }

    @Override
    public void close() throws IOException {
        state = WAITING;
    }
}
