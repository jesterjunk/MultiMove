package com.wurmonline.server.questions;
import com.wurmonline.server.players.Player;
import java.util.Properties;
public abstract class Question {
    protected final Player responder;
    public Question(Player player) {
        responder = player;
        Questions.addQuestion(this);
    }
    public final Player getResponder() { return responder; }
    public void timedOut() { }
    public abstract void sendQuestion();
    public abstract void answer(Properties answers);
}
