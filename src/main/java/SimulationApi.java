import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Простий HTTP сервер для керування симуляцією з-поза Docker.
 *
 * Запуск сценарію:
 *   curl http://localhost:8161/simulate?scenario=reb
 *   curl http://localhost:8161/simulate?scenario=lateral
 *   curl http://localhost:8161/simulate?scenario=encryption
 *   curl http://localhost:8161/simulate?scenario=reset
 */
public class SimulationApi {

    private final SnmpAgent agent;
    private final HttpServer server;

    public SimulationApi(SnmpAgent agent, int port) throws IOException {
        this.agent = agent;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // Endpoint: GET /simulate?scenario=reb
        server.createContext("/simulate", exchange -> {
            String query = exchange.getRequestURI().getQuery(); // "scenario=reb"
            String scenario = query != null ? query.replace("scenario=", "") : "";

            String response = handleScenario(scenario);

            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });

        // Endpoint: GET /status — поточні значення метрик
        server.createContext("/status", exchange -> {
            String response = "Agent is running";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });
    }

    private String handleScenario(String scenario) {
        return switch (scenario) {
            case "reb" -> {
                // Знижуємо SNR поступово
                new Thread(() -> {
                    for (int i = 0; i < 5; i++) {
                        agent.simulateREB(8);
                        try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                    }
                }).start();
                yield "REB simulation started. SNR dropping...";
            }
            case "lateral" -> {
                agent.simulatePacketLoss(200);
                agent.simulateHighLoad(88);
                yield "Lateral Movement simulation started. ifInErrors=200, CPU=88%";
            }
            case "encryption" -> {
                agent.simulateEncryptionFailure();
                yield "Encryption failure simulation started. encryptionStatus=0";
            }
            case "reset" -> {
                agent.resetToNormal();
                yield "Agent state reset to normal.";
            }
            default -> "Unknown scenario. Use: reb, lateral, encryption, reset";
        };
    }

    public void start() {
        server.start();
        System.out.println("[API] HTTP API запущено на порту " +
                server.getAddress().getPort());
        System.out.println("[API] Використання: curl http://localhost:8161/simulate?scenario=reb");
    }
}
