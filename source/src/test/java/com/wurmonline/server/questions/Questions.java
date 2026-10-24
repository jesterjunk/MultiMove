package com.wurmonline.server.questions;
import java.util.*;
public final class Questions {
    public static final Set<Question> active = new HashSet<>();
    public static void addQuestion(Question q) {
        active.add(q);
        Question last = q.getResponder().current;
        if (last != null) last.timedOut();
        q.getResponder().current = q;
    }
    public static void removeQuestion(Question q) { active.remove(q); }
}
