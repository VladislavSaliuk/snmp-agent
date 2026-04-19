import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Тести для SnmpAgent.
 * Перевіряємо початковий стан MIB та всі simulation hooks.
 * Використовуємо рефлексію для прямого доступу до MIB таблиці.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnmpAgentTest {

    private SnmpAgent agent;

    private static final String OID_ENCRYPTION = "1.3.6.1.4.1.99999.1.0";
    private static final String OID_SNR = "1.3.6.1.4.1.99999.2.0";
    private static final String OID_TEMPERATURE = "1.3.6.1.4.1.99999.3.0";
    private static final String OID_IF_IN_ERR = "1.3.6.1.2.1.2.2.1.14.1";
    private static final String OID_IF_OUT_DISC = "1.3.6.1.2.1.2.2.1.19.1";
    private static final String OID_CPU_LOAD = "1.3.6.1.4.1.2021.11.11.0";

    @BeforeEach
    void setUp() throws Exception {
        agent = new SnmpAgent("0.0.0.0/19999");
        agent.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        agent.stop();
    }

    @SuppressWarnings("unchecked")
    private int getMibValue(String oid) throws Exception {
        Field f = SnmpAgent.class.getDeclaredField("mib");
        f.setAccessible(true);
        Map<OID, Variable> mib = (Map<OID, Variable>) f.get(agent);
        Variable val = mib.get(new OID(oid));
        assertNotNull(val, "OID " + oid + " не знайдено в MIB");
        return Integer.parseInt(val.toString());
    }

    // ── Початковий стан ───────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("encryptionStatus = 1 при старті")
    void initialEncryption() throws Exception {
        assertEquals(1, getMibValue(OID_ENCRYPTION));
    }

    @Test
    @Order(2)
    @DisplayName("SNR = 35 dB при старті")
    void initialSnr() throws Exception {
        assertEquals(35, getMibValue(OID_SNR));
    }

    @Test
    @Order(3)
    @DisplayName("temperature = 42°C при старті")
    void initialTemperature() throws Exception {
        assertEquals(42, getMibValue(OID_TEMPERATURE));
    }

    @Test
    @Order(4)
    @DisplayName("ifInErrors = 0 при старті")
    void initialIfInErrors() throws Exception {
        assertEquals(0, getMibValue(OID_IF_IN_ERR));
    }

    @Test
    @Order(5)
    @DisplayName("CPU load = 15% при старті")
    void initialCpuLoad() throws Exception {
        assertEquals(15, getMibValue(OID_CPU_LOAD));
    }

    // ── simulatePacketLoss ────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("simulatePacketLoss: ifInErrors збільшується")
    void packetLossIncreasesErrors() throws Exception {
        agent.simulatePacketLoss(200);
        assertEquals(200, getMibValue(OID_IF_IN_ERR));
    }

    @Test
    @Order(7)
    @DisplayName("simulatePacketLoss: ifOutDiscards = errors/2")
    void packetLossSetsOutDiscards() throws Exception {
        agent.simulatePacketLoss(100);
        assertEquals(50, getMibValue(OID_IF_OUT_DISC));
    }

    @Test
    @Order(8)
    @DisplayName("simulatePacketLoss: накопичується при повторних викликах")
    void packetLossAccumulates() throws Exception {
        agent.simulatePacketLoss(100);
        agent.simulatePacketLoss(100);
        assertEquals(200, getMibValue(OID_IF_IN_ERR));
    }

    // ── simulateREB ───────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("simulateREB: SNR зменшується")
    void rebDecreasesSnr() throws Exception {
        agent.simulateREB(10);
        assertEquals(25, getMibValue(OID_SNR));
    }

    @Test
    @Order(10)
    @DisplayName("simulateREB: SNR не менше 0")
    void rebSnrNotNegative() throws Exception {
        agent.simulateREB(999);
        assertEquals(0, getMibValue(OID_SNR));
    }

    @Test
    @Order(11)
    @DisplayName("simulateREB: SNR падає поступово")
    void rebAccumulates() throws Exception {
        agent.simulateREB(5);
        agent.simulateREB(5);
        agent.simulateREB(5);
        assertEquals(20, getMibValue(OID_SNR));
    }

    // ── simulateHighLoad ──────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("simulateHighLoad: CPU змінюється")
    void highLoad() throws Exception {
        agent.simulateHighLoad(95);
        assertEquals(95, getMibValue(OID_CPU_LOAD));
    }

    @Test
    @Order(13)
    @DisplayName("simulateHighLoad: CPU не більше 100%")
    void highLoadMax() throws Exception {
        agent.simulateHighLoad(150);
        assertEquals(100, getMibValue(OID_CPU_LOAD));
    }

    // ── simulateEncryptionFailure ─────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("simulateEncryptionFailure: encryptionStatus = 0")
    void encryptionFailure() throws Exception {
        agent.simulateEncryptionFailure();
        assertEquals(0, getMibValue(OID_ENCRYPTION));
    }

    // ── resetToNormal ─────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("resetToNormal: всі метрики повертаються до початкових")
    void resetRestoresAll() throws Exception {
        agent.simulatePacketLoss(500);
        agent.simulateREB(30);
        agent.simulateHighLoad(99);
        agent.simulateEncryptionFailure();
        agent.resetToNormal();

        assertAll(
                () -> assertEquals(0, getMibValue(OID_IF_IN_ERR), "ifInErrors"),
                () -> assertEquals(35, getMibValue(OID_SNR), "SNR"),
                () -> assertEquals(15, getMibValue(OID_CPU_LOAD), "CPU"),
                () -> assertEquals(1, getMibValue(OID_ENCRYPTION), "encryption"),
                () -> assertEquals(42, getMibValue(OID_TEMPERATURE), "temperature")
        );
    }

    // ── processPdu (через мережу) ─────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("processPdu: агент відповідає на SNMP GET запит")
    void processPduHandlesGetRequest() throws Exception {
        SnmpTester tester = new SnmpTester();
        String val = tester.get("127.0.0.1/19999", OID_SNR);
        assertEquals("35", val);
    }

    @Test
    @Order(17)
    @DisplayName("processPdu: повертає оновлене значення після зміни метрики")
    void processPduReturnsUpdatedValue() throws Exception {
        agent.simulateREB(15);
        SnmpTester tester = new SnmpTester();
        String val = tester.get("127.0.0.1/19999", OID_SNR);
        assertEquals("20", val);
    }

    @Test
    @Order(18)
    @DisplayName("processPdu: відповідає на запит encryptionStatus")
    void processPduEncryption() throws Exception {
        SnmpTester tester = new SnmpTester();
        String val = tester.get("127.0.0.1/19999", OID_ENCRYPTION);
        assertEquals("1", val);
    }

    @Test
    @Order(19)
    @DisplayName("stop: працює коли snmp == null (не запущений)")
    void stopWhenNotStarted() throws Exception {
        SnmpAgent notStarted = new SnmpAgent("0.0.0.0/19996");
        // Не викликаємо start() — snmp залишається null
        assertDoesNotThrow(() -> notStarted.stop());
    }

    @Test
    @Order(20)
    @DisplayName("processPdu: ігнорує не-GET запити (SET)")
    void processPduIgnoresNonGet() throws Exception {
        SnmpTester tester = new SnmpTester();
        // Надсилаємо SET запит — агент має його проігнорувати
        org.snmp4j.CommunityTarget<org.snmp4j.smi.UdpAddress> target = new org.snmp4j.CommunityTarget<>();
        target.setAddress(new org.snmp4j.smi.UdpAddress("127.0.0.1/19999"));
        target.setCommunity(new org.snmp4j.smi.OctetString("public"));
        target.setVersion(org.snmp4j.mp.SnmpConstants.version2c);
        target.setTimeout(2000);
        target.setRetries(0);

        org.snmp4j.PDU pdu = new org.snmp4j.PDU();
        pdu.setType(org.snmp4j.PDU.SET);
        pdu.add(new org.snmp4j.smi.VariableBinding(
                new OID(OID_SNR), new org.snmp4j.smi.Integer32(99)
        ));

        org.snmp4j.transport.DefaultUdpTransportMapping t =
                new org.snmp4j.transport.DefaultUdpTransportMapping();
        org.snmp4j.Snmp snmp = new org.snmp4j.Snmp(t);
        t.listen();
        snmp.send(pdu, target);
        snmp.close();

        // SNR має залишитись 35 — SET запит проігнорований
        Thread.sleep(500);
        assertEquals(35, getMibValue(OID_SNR));
    }

}