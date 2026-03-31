import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws IOException {

        String agentAddr = "0.0.0.0/16100"; // порт > 1024 — не потребує прав адміна
        String managerAddr = args.length >= 1 ? args[0] : "127.0.0.1/162";

        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║  SNMP Agent — Special Purpose Network Sim  ║");
        System.out.println("╚════════════════════════════════════════════╝");
        System.out.println("Агент слухає на: UDP " + agentAddr);
        System.out.println("Trap-и → " + managerAddr + "\n");

        SnmpAgent agent = new SnmpAgent(agentAddr);
        agent.start(); // відкриває UDP сокет, реєструє CommandResponder

        System.out.println("✓ Агент запущено!");
        System.out.println("  Перевір: snmpwalk -v2c -c public 127.0.0.1");
        System.out.println("  Має повернути sysName = SpecNet-Agent-01\n");

        TrapSender traps = new TrapSender(managerAddr);
        SimulationEngine sim = new SimulationEngine(agent, traps);

        printMenu();
        Scanner sc = new Scanner(System.in);

        while (sc.hasNextLine()) {
            String cmd = sc.nextLine().trim().toLowerCase();
            switch (cmd) {
                case "1" -> sim.runLateralMovement();
                case "2" -> sim.runREB();
                case "3" -> sim.runEncryptionFailure();
                case "r" -> {
                    agent.resetToNormal();
                    System.out.println("Стан відновлено до нормального.");
                }
                case "m" -> printMenu();
                case "q" -> {
                    sim.stop();
                    traps.close();
                    agent.stop();
                    System.out.println("Агент зупинено. До побачення!");
                    System.exit(0);
                }
                default -> System.out.println("Невідома команда. [m] — показати меню.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("""
                ┌──────────────────────────────────────────┐
                │  [1] Сценарій: Lateral Movement          │
                │  [2] Сценарій: РЕБ (придушення сигналу) │
                │  [3] Сценарій: Збій шифрування           │
                │  [R] Скинути стан до нормального         │
                │  [M] Показати меню                       │
                │  [Q] Вийти                               │
                └──────────────────────────────────────────┘""");
    }
}