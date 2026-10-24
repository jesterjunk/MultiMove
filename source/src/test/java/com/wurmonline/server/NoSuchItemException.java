package com.wurmonline.server;
public class NoSuchItemException extends Exception {
    public NoSuchItemException(long id) { super("Missing item " + id); }
}
