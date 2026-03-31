// "Режисер" симуляції. Знає коли і що зробити.
// Використовує ScheduledExecutorService — планувальник завдань Java,
// що дозволяє запускати дії з затримкою без блокування головного потоку.
//
// Приклад:
//   at(3, () -> agent.simulateHighLoad(88))
//   означає "через 3 секунди підняти CPU до 88%"
//

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SimulationEngine {

    private final SnmpAgent agent;
    private final TrapSender traps;

    // Планувальник з 2 потоками — один для агента, другий для Trap-ів
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);

    public SimulationEngine(SnmpAgent agent, TrapSender traps) {
        this.agent = agent;
        this.traps = traps;
    }

    // ─────────────────────────────────────────────────────────────────
    // СЦЕНАРІЙ 1: Lateral Movement (горизонтальне переміщення атаки)
    // ─────────────────────────────────────────────────────────────────
    // Що відбувається в реальності:
    //   Шкідливе ПЗ потрапило на термінал оператора підстанції.
    //   Тепер воно намагається знайти IED-контролер (керує вимикачами),
    //   надсилаючи багато пакетів на різні IP (сканування Modbus TCP порт 502).
    //
    // Що бачить Zabbix:
    //   1. Різкий ріст ifInErrors → Trap про втрату пакетів
    //   2. CPU стрибає до 88% → Trap про перевантаження
    //   3. Алерт: "Несанкціоноване сканування з вузла OPERATOR-1"
    //   4. Автоматична ізоляція порту через SNMP SetRequest
    //
    public void runLateralMovement() {
        banner("СЦЕНАРІЙ 1: Lateral Movement — Modbus TCP Scan");

        at(1, () -> {
            System.out.println("  → Агент імітує масові пакети до різних IP (порт 502)");
            agent.simulatePacketLoss(200); // ifInErrors += 200
        });
        at(3, () -> {
            agent.simulateHighLoad(88);
            quietly(() -> traps.sendHighLoadTrap(88)); // Trap → Zabbix
        });
        at(5, () -> {
            quietly(() -> traps.sendPacketLossTrap(200));
            System.out.println("  [ALERT] Несанкціоноване сканування Modbus TCP!");
        });
        at(8, () -> {
            System.out.println("  → Ізоляція порту (SetRequest → ifAdminStatus=down)");
            System.out.println("  ⏳ Запусти SnmpTester зараз щоб побачити ifInErrors=200!");
        });
        at(20, () -> {
            agent.resetToNormal();
            System.out.println("  ✓ Сценарій завершено.\n");
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // СЦЕНАРІЙ 2: РЕБ (Радіоелектронна боротьба)
    // ─────────────────────────────────────────────────────────────────
    // Що відбувається в реальності:
    //   Ворог використовує засоби РЕБ для придушення радіоканалу.
    //   Рівень SNR (сигнал/шум) поступово падає.
    //   При SNR < 10 dB зв'язок деградує, виникають затримки та втрати.
    //
    // Унікальність нашого агента:
    //   Ми передаємо SNR через Custom MIB (OID 1.3.6.1.4.1.99999.2.0).
    //   Стандартне обладнання Cisco/Juniper такого поля не має.
    //   Це дозволяє розрізнити: "зламалась антена" vs "ворог глушить сигнал".
    //
    public void runREB() {
        banner("СЦЕНАРІЙ 2: Імітація РЕБ — придушення сигналу");

        // SNR знижується на 8 dB кожні 2 секунди (35→27→19→11→3 dB)
        for (int i = 1; i <= 5; i++) {
            final int step = i;
            at(i * 2L, () -> {
                agent.simulateREB(8);
                System.out.printf("  → Крок %d: SNR знижується...%n", step);
                if (step == 4) { // при 4-му кроці SNR < 10 → критично
                    int criticalSnr = 35 - 8 * step;
                    quietly(() -> traps.sendREBTrap(criticalSnr));
                }
            });
        }
        at(12, () -> {
            agent.resetToNormal();
            System.out.println("  ✓ Завади зникли. Сигнал відновлено.\n");
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // СЦЕНАРІЙ 3: Збій шифрування
    // ─────────────────────────────────────────────────────────────────
    // Що відбувається в реальності:
    //   Скомпрометований вузол переходить на передачу у відкритому вигляді.
    //   encryptionStatus = 0 → критична тривога в Zabbix.
    //   Оператор має негайно ініціювати ротацію ключів.
    //
    public void runEncryptionFailure() {
        banner("СЦЕНАРІЙ 3: Збій шифрування — відкрита передача даних");

        at(1, () -> {
            agent.simulateEncryptionFailure();
            quietly(() -> traps.sendEncryptionTrap());
        });
        at(4, () -> {
            System.out.println("  [ACTION] Оператор отримав алерт. Ротація ключів...");
            agent.resetToNormal();
            System.out.println("  ✓ Шифрування відновлено.\n");
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // Допоміжні методи
    // ─────────────────────────────────────────────────────────────────

    /**
     * Запускає задачу через delaySec секунд
     */
    private void at(long delaySec, Runnable task) {
        scheduler.schedule(task, delaySec, TimeUnit.SECONDS);
    }

    /**
     * Виконує IORunnable без пробросу IOException (для лямбд)
     */
    private void quietly(IORunnable task) {
        try {
            task.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void banner(String text) {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║  " + text);
        System.out.println("╚══════════════════════════════════════════╝");
    }

    public void stop() {
        scheduler.shutdown();
    }

    @FunctionalInterface
    interface IORunnable {
        void run() throws IOException;
    }
}