import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * SNMP-агент без BaseAgent — пряма обробка PDU через CommandResponder.
 * <p>
 * Як працює:
 * 1. Відкриваємо UDP сокет на порту 16100
 * 2. Реєструємо CommandResponder — він отримує кожен вхідний пакет
 * 3. При GetRequest — шукаємо OID у нашій таблиці і повертаємо значення
 * 4. Simulation hooks просто змінюють значення у таблиці
 */
public class SnmpAgent implements CommandResponder {

    // Таблиця OID → значення (наш "MIB реєстр")
    private final Map<OID, Variable> mib = new HashMap<>();

    // Стандартні OID-и
    private static final OID SYS_DESCR = new OID("1.3.6.1.2.1.1.1.0");
    private static final OID SYS_NAME = new OID("1.3.6.1.2.1.1.5.0");
    private static final OID IF_IN_ERR = new OID("1.3.6.1.2.1.2.2.1.14.1");
    private static final OID IF_OUT_DISC = new OID("1.3.6.1.2.1.2.2.1.19.1");
    private static final OID CPU_LOAD = new OID("1.3.6.1.4.1.2021.11.11.0");

    // Custom MIB OID-и (Enterprise 99999)
    private static final OID ENCRYPTION = new OID("1.3.6.1.4.1.99999.1.0");
    private static final OID SNR = new OID("1.3.6.1.4.1.99999.2.0");
    private static final OID TEMPERATURE = new OID("1.3.6.1.4.1.99999.3.0");

    private Snmp snmp;
    private final String listenAddress;

    public SnmpAgent(String listenAddress) {
        this.listenAddress = listenAddress;
        initMib(); // заповнюємо таблицю початковими значеннями
    }

    /**
     * Початкові значення всіх OID-ів
     */
    private void initMib() {
        mib.put(SYS_DESCR, new OctetString("Diploma SNMP Agent v1.0 | Special Purpose Network Simulator"));
        mib.put(SYS_NAME, new OctetString("SpecNet-Agent-01"));
        mib.put(IF_IN_ERR, new Integer32(0));
        mib.put(IF_OUT_DISC, new Integer32(0));
        mib.put(CPU_LOAD, new Integer32(15));   // 15% CPU — норма
        mib.put(ENCRYPTION, new Integer32(1));    // 1 = зашифровано
        mib.put(SNR, new Integer32(35));   // 35 dB — добрий сигнал
        mib.put(TEMPERATURE, new Integer32(42));   // 42°C — норма
    }

    /**
     * Запускає агента — відкриває UDP сокет і починає слухати
     */
    public void start() throws IOException {
        DefaultUdpTransportMapping transport =
                new DefaultUdpTransportMapping(new UdpAddress(listenAddress));
        snmp = new Snmp(transport);
        snmp.addCommandResponder(this); // реєструємо себе як обробник запитів
        transport.listen();
        System.out.println("[AGENT] Слухаю на " + listenAddress);
    }

    public void stop() throws IOException {
        if (snmp != null) snmp.close();
    }

    // ════════════════════════════════════════════════════════════════
    // Обробник вхідних SNMP запитів
    // Викликається автоматично при кожному GetRequest від Zabbix/SnmpTester
    // ════════════════════════════════════════════════════════════════
    @Override
    public synchronized void processPdu(CommandResponderEvent event) {
        PDU req = event.getPDU();
        if (req == null) return;

        System.out.println("[AGENT] Отримано запит типу: " + PDU.getTypeString(req.getType()));

        // Обробляємо тільки GET запити
        if (req.getType() != PDU.GET && req.getType() != PDU.GETNEXT) return;

        PDU resp = new PDU();
        resp.setType(PDU.RESPONSE);
        resp.setRequestID(req.getRequestID());

        // Для кожного запитуваного OID — знаходимо значення
        for (VariableBinding vb : req.getVariableBindings()) {
            OID oid = vb.getOid();
            Variable value = mib.getOrDefault(oid, Null.noSuchObject);
            resp.add(new VariableBinding(oid, value));
            System.out.println("[AGENT] GET " + oid + " = " + value);
        }

        // Надсилаємо відповідь
        try {
            StatusInformation status = new StatusInformation();
            event.getMessageDispatcher().returnResponsePdu(
                    event.getMessageProcessingModel(),
                    event.getSecurityModel(),
                    event.getSecurityName(),
                    event.getSecurityLevel(),
                    resp,
                    event.getMaxSizeResponsePDU(),
                    event.getStateReference(),
                    status
            );
        } catch (Exception e) {
            System.err.println("[AGENT] Помилка відправки відповіді: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SIMULATION HOOKS — змінюють значення у таблиці mib
    // ════════════════════════════════════════════════════════════════

    public void simulatePacketLoss(int errors) {
        int val = ((Integer32) mib.get(IF_IN_ERR)).toInt() + errors;
        mib.put(IF_IN_ERR, new Integer32(val));
        mib.put(IF_OUT_DISC, new Integer32(val / 2));
        log("[SIM] Packet loss → ifInErrors=%d", val);
    }

    public void simulateREB(int dropDb) {
        int val = Math.max(0, ((Integer32) mib.get(SNR)).toInt() - dropDb);
        mib.put(SNR, new Integer32(val));
        log("[SIM] REB → SNR=%d dB", val);
        if (val < 10) log("[ALERT] SNR критичний!");
    }

    public void simulateHighLoad(int pct) {
        mib.put(CPU_LOAD, new Integer32(Math.min(100, pct)));
        log("[SIM] CPU load → %d%%", pct);
    }

    public void simulateEncryptionFailure() {
        mib.put(ENCRYPTION, new Integer32(0));
        log("[ALERT] Шифрування ВІДКЛЮЧЕНО!");
    }

    public void resetToNormal() {
        initMib(); // просто перезаповнюємо таблицю початковими значеннями
        log("[SIM] Стан відновлено до НОРМАЛЬНОГО.");
    }

    private void log(String fmt, Object... args) {
        System.out.printf(fmt + "%n", args);
    }
}