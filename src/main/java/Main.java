import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {

        String deviceName = System.getenv().getOrDefault("DEVICE_NAME", "SpecNet-Agent-01");
        String deviceType = System.getenv("DEVICE_TYPE"); // null якщо не в Docker
        boolean isDocker  = deviceType != null;
        // Docker → порт 161, локально → 16100 (не потребує прав адміна)
        String agentAddr  = isDocker ? "0.0.0.0/161" : "0.0.0.0/16100";
        String managerAddr = args.length >= 1 ? args[0] : "127.0.0.1/162";

        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║  SNMP Agent — Special Purpose Network Sim  ║");
        System.out.println("╚════════════════════════════════════════════╝");
        System.out.println("Пристрій : " + deviceName);
        System.out.println("Агент    : UDP " + agentAddr);
        System.out.println("Trap-и  → " + managerAddr + "\n");

        SnmpAgent agent = new SnmpAgent(agentAddr);
        agent.start();

        System.out.println("✓ Агент запущено!");

        TrapSender traps = new TrapSender(managerAddr);
        SimulationEngine sim = new SimulationEngine(agent, traps);

        if (isDocker) {
            System.out.println("✓ Docker-режим. Очікую SNMP запити...");
            while (true) {
                Thread.sleep(5000);
            }

        } else {
            // ЛОКАЛЬНИЙ РЕЖИМ — інтерактивне меню
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