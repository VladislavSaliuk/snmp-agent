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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тести для SimulationEngine.
 * Перевіряємо що сценарії коректно змінюють метрики агента.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimulationEngineTest {

    private SnmpAgent agent;
    private TrapSender traps;
    private SimulationEngine engine;

    private static final String OID_ENCRYPTION = "1.3.6.1.4.1.99999.1.0";
    private static final String OID_SNR = "1.3.6.1.4.1.99999.2.0";
    private static final String OID_IF_IN_ERR = "1.3.6.1.2.1.2.2.1.14.1";
    private static final String OID_CPU_LOAD = "1.3.6.1.4.1.2021.11.11.0";

    @BeforeEach
    void setUp() throws Exception {
        agent = new SnmpAgent("0.0.0.0/19998");
        agent.start();
        traps = new TrapSender("127.0.0.1/19162");
        engine = new SimulationEngine(agent, traps);
    }

    @AfterEach
    void tearDown() throws Exception {
        engine.stop();
        traps.close();
        agent.stop();
    }

    @SuppressWarnings("unchecked")
    private int getMibValue(String oid) throws Exception {
        Field f = SnmpAgent.class.getDeclaredField("mib");
        f.setAccessible(true);
        Map<OID, Variable> mib = (Map<OID, Variable>) f.get(agent);
        Variable val = mib.get(new OID(oid));
        assertNotNull(val, "OID " + oid + " не знайдено");
        return Integer.parseInt(val.toString());
    }

    @Test
    @Order(1)
    @DisplayName("SimulationEngine: створюється без помилок")
    void engineCreated() {
        assertNotNull(engine);
    }

    @Test
    @Order(2)
    @DisplayName("runLateralMovement: запускається без помилок")
    void lateralMovementRuns() {
        assertDoesNotThrow(() -> engine.runLateralMovement());
    }

    @Test
    @Order(3)
    @DisplayName("runLateralMovement: ifInErrors збільшується після виконання")
    void lateralMovementChangesMetrics() throws Exception {
        engine.runLateralMovement();
        Thread.sleep(2000);
        assertTrue(getMibValue(OID_IF_IN_ERR) > 0);
    }

    @Test
    @Order(4)
    @DisplayName("runREB: запускається без помилок")
    void rebRuns() {
        assertDoesNotThrow(() -> engine.runREB());
    }

    @Test
    @Order(5)
    @DisplayName("runREB: SNR знижується після виконання")
    void rebLowersSnr() throws Exception {
        engine.runREB();
        Thread.sleep(3000);
        assertTrue(getMibValue(OID_SNR) < 35);
    }

    @Test
    @Order(6)
    @DisplayName("runEncryptionFailure: запускається без помилок")
    void encryptionFailureRuns() {
        assertDoesNotThrow(() -> engine.runEncryptionFailure());
    }

    @Test
    @Order(7)
    @DisplayName("runEncryptionFailure: encryptionStatus = 0")
    void encryptionFailureSetsZero() throws Exception {
        engine.runEncryptionFailure();
        Thread.sleep(2000);
        assertEquals(0, getMibValue(OID_ENCRYPTION));
    }

    @Test
    @Order(8)
    @DisplayName("stop: зупиняється без помилок")
    void stopRuns() {
        assertDoesNotThrow(() -> engine.stop());
    }
}