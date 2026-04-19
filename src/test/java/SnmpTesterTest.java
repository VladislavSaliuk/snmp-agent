import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Тести для SnmpTester.
 * Перевіряємо метод get() та main().
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnmpTesterTest {

    private SnmpAgent agent;
    private SnmpTester tester;

    @BeforeEach
    void setUp() throws Exception {
        agent = new SnmpAgent("0.0.0.0/19997");
        agent.start();
        tester = new SnmpTester();
    }

    @AfterEach
    void tearDown() throws Exception {
        agent.stop();
    }

    @Test
    @Order(1)
    @DisplayName("get: повертає значення SNR")
    void getSnr() {
        String val = tester.get("127.0.0.1/19997", "1.3.6.1.4.1.99999.2.0");
        assertEquals("35", val);
    }

    @Test
    @Order(2)
    @DisplayName("get: повертає значення encryptionStatus")
    void getEncryption() {
        String val = tester.get("127.0.0.1/19997", "1.3.6.1.4.1.99999.1.0");
        assertEquals("1", val);
    }

    @Test
    @Order(3)
    @DisplayName("get: повертає null якщо агент недоступний")
    void getTimeout() {
        String val = tester.get("127.0.0.1/19000", "1.3.6.1.4.1.99999.2.0");
        assertNull(val);
    }

    @Test
    @Order(4)
    @DisplayName("main: виконується без помилок з відповіддю агента")
    void mainWithAgent() throws Exception {
        SnmpAgent agent16100 = new SnmpAgent("0.0.0.0/16100");
        agent16100.start();
        assertDoesNotThrow(() -> SnmpTester.main(new String[]{}));
        agent16100.stop();
    }

    @Test
    @Order(5)
    @DisplayName("main: виконується без помилок з TIMEOUT")
    void mainTimeout() {
        assertDoesNotThrow(() -> SnmpTester.main(new String[]{}));
    }
}