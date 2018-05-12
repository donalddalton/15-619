package frontend.controller;

import frontend.controller.Action;
import io.undertow.server.HttpServerExchange;

public class HeartbeatAction extends Action {
    public String handleGet(final HttpServerExchange exchange)  { return "heartbeat";         }
    public String handlePost(final HttpServerExchange exchange) { return handleGet(exchange); }
}
