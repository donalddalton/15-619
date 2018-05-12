package frontend.controller;

import frontend.dao.MySQLDAO;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.JBossLoggingAccessLogReceiver;
import org.apache.commons.cli.*;

import java.net.URISyntaxException;


public class Controller {
    public static void main(final String[] args) throws URISyntaxException {
        Options options = new Options();
        options.addOption("debug", false, "Launch in debug mode.");
        options.addOption("username", true, "MySQL database username.");
        options.addOption("password", true, "MySQL database password.");
        options.addOption("ip", true, "MySQL database endpoint.");
        CommandLineParser parser = new DefaultParser();
        try {

            CommandLine cmd = parser.parse(options, args);
            boolean DEBUG = cmd.hasOption("debug");
            String username = cmd.getOptionValue("username");
            String password = cmd.getOptionValue("password");
            String ip = cmd.getOptionValue("ip");

            MySQLDAO dao = new MySQLDAO(ip, username, password);

            int port;
            String host;
            HttpHandler handler;
            if (DEBUG) {
                port = 8080;
                host = "localhost";
                handler = new AccessLogHandler(
                        new RoutingHandler()
                                .get("/q1", new BlockingHandler(new Q1Action()))
                                .get("/q2", new BlockingHandler(new Q2Action(dao)))
                                .get("/q3", new BlockingHandler(new Q3Action(dao)))
                                .get("/q4", new BlockingHandler(new Q4Action(dao)))
                                .setFallbackHandler(new HeartbeatAction() {
                                }),
                        new JBossLoggingAccessLogReceiver(),
                        "combined",
                        Controller.class.getClassLoader()
                );
            } else {
                port = 80;
                host = "0.0.0.0";
                handler = new RoutingHandler()
                        .get("/q1", new BlockingHandler(new Q1Action()))
                        .get("/q2", new BlockingHandler(new Q2Action(dao)))
                        .get("/q3", new BlockingHandler(new Q3Action(dao)))
                        .get("/q4", new BlockingHandler(new Q4Action(dao)))
                        .setFallbackHandler(new HeartbeatAction() {
                        });
            }

            Undertow.builder()
                    .setWorkerThreads(20)
                    .setIoThreads(10)
                    .setBufferSize(16*1024)
                    .addHttpListener(port, host)
                    .setHandler(handler)
                    .build()
                    .start();

        } catch (ParseException e) {
            System.out.println(e);
        }
    }
}

