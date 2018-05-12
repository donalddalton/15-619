package frontend.controller;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;


public abstract class Action implements HttpHandler {
    /**
     * Method to perform logic to serve GET requests
     * @param exchange the exchange object
     * @return the response string
     */
    public abstract String handleGet(final HttpServerExchange exchange);

    /**
     * Method to perform logic to serve POST requests
     * @param exchange the exchange object
     * @return the response string
     */
    public abstract String handlePost(final HttpServerExchange exchange);

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        String response;
        if (Methods.GET.equals(exchange.getRequestMethod()))       { response = handleGet(exchange);  }
        else if (Methods.POST.equals(exchange.getRequestMethod())) { response = handlePost(exchange); }
        else                                                       { response = "heartbeat";          }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send(response);
    }
}
