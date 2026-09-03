package network.messages.game;

import network.messages.Message;

public class ErrorResponse extends Message {
    private final String errorMessage;

    public ErrorResponse(String errorMessage) {
        super("ERROR_RESPONSE");
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() { return errorMessage; }
}