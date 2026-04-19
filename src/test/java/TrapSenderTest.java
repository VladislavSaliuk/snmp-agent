import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Тести для TrapSender.
 * Перевіряємо що всі типи трапів надсилаються без помилок.
 * Zabbix не потрібен — пакети просто не дійдуть але код виконається.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrapSenderTest {

    private TrapSender sender;

    @BeforeEach
    void setUp() throws Exception {
        sender = new TrapSender("127.0.0.1/19162");
    }

    @AfterEach
    void tearDown() throws Exception {
        sender.close();
    }

    @Test
    @Order(1)
    @DisplayName("TrapSender: створюється без помилок")
    void created() {
        assertNotNull(sender);
    }

    @Test
    @Order(2)
    @DisplayName("sendPacketLossTrap: надсилається без помилок")
    void packetLossTrap() {
        assertDoesNotThrow(() -> sender.sendPacketLossTrap(200));
    }

    @Test
    @Order(3)
    @DisplayName("sendREBTrap: надсилається без помилок")
    void rebTrap() {
        assertDoesNotThrow(() -> sender.sendREBTrap(5));
    }

    @Test
    @Order(4)
    @DisplayName("sendEncryptionTrap: надсилається без помилок")
    void encryptionTrap() {
        assertDoesNotThrow(() -> sender.sendEncryptionTrap());
    }

    @Test
    @Order(5)
    @DisplayName("sendHighLoadTrap: надсилається без помилок")
    void highLoadTrap() {
        assertDoesNotThrow(() -> sender.sendHighLoadTrap(95));
    }

    @Test
    @Order(6)
    @DisplayName("send: базовий метод з кастомним OID")
    void sendCustom() {
        assertDoesNotThrow(() -> sender.send(
                TrapSender.TRAP_PACKET_LOSS, "Test message"
        ));
    }

    @Test
    @Order(7)
    @DisplayName("close: закривається без помилок")
    void closes() {
        assertDoesNotThrow(() -> sender.close());
    }
}