// TRAP — це асинхронне повідомлення від агента до менеджера (Zabbix).
// На відміну від звичайного SNMP (де Zabbix питає → агент відповідає),
// Trap надсилається агентом самостійно, коли стається щось важливе.
//
// Приклад: CPU піднявся до 97% → агент одразу надсилає Trap до Zabbix
// → Zabbix показує алерт на дашборді, не чекаючи наступного polling.
//

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;

public class TrapSender {

    // OID-и наших Trap-ів (гілка .10.x зарезервована для сповіщень)
    public static final OID TRAP_PACKET_LOSS = new OID("1.3.6.1.4.1.99999.10.1");
    public static final OID TRAP_REB = new OID("1.3.6.1.4.1.99999.10.2");
    public static final OID TRAP_ENCRYPTION = new OID("1.3.6.1.4.1.99999.10.3");
    public static final OID TRAP_HIGH_LOAD = new OID("1.3.6.1.4.1.99999.10.4");

    // OID для текстового повідомлення всередині Trap-а
    private static final OID OID_TRAP_MSG = new OID("1.3.6.1.4.1.99999.99.1");

    private final Snmp snmp;
    private final CommunityTarget<UdpAddress> target;

    /**
     * @param managerAddress адреса Zabbix куди надсилати Trap-и
     *                       Формат: "127.0.0.1/162"  (порт 162 — стандарт для Trap)
     */
    public TrapSender(String managerAddress) throws IOException {
        // Відкриваємо UDP транспорт для надсилання
        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
        this.snmp = new Snmp(transport);
        transport.listen();

        // Налаштовуємо ціль (Zabbix)
        this.target = new CommunityTarget<>();
        this.target.setAddress(new UdpAddress(managerAddress));
        this.target.setCommunity(new OctetString("public")); // той самий "пароль"
        this.target.setVersion(SnmpConstants.version2c);
        this.target.setRetries(1);    // повторити якщо не дійшло
        this.target.setTimeout(2000); // чекати відповідь 2 секунди
    }

    /**
     * Базовий метод надсилання Trap.
     * PDU типу NOTIFICATION = SNMPv2c Trap.
     */
    public void send(OID trapOid, String message) throws IOException {
        PDU pdu = new PDU();
        pdu.setType(PDU.NOTIFICATION);

        // Обов'язкові поля кожного Trap-а за RFC 3416:
        pdu.add(new VariableBinding(SnmpConstants.sysUpTime,
                new TimeTicks(System.currentTimeMillis() / 10))); // uptime в 0.01 сек
        pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, trapOid)); // тип події

        // Наше кастомне текстове повідомлення
        pdu.add(new VariableBinding(OID_TRAP_MSG, new OctetString(message)));

        snmp.send(pdu, target);
        System.out.printf("[TRAP → %s] %s%n", target.getAddress(), message);
    }

    // Зручні методи для конкретних сценаріїв
    public void sendPacketLossTrap(int errors) throws IOException {
        send(TRAP_PACKET_LOSS,
                "Втрата пакетів: ifInErrors=" + errors + " (можливий Lateral Movement)");
    }

    public void sendREBTrap(int snrValue) throws IOException {
        send(TRAP_REB, "РЕБ: SNR=" + snrValue + " dB. Можливе придушення сигналу.");
    }

    public void sendEncryptionTrap() throws IOException {
        send(TRAP_ENCRYPTION, "КРИТИЧНО: Шифрування відключено!");
    }

    public void sendHighLoadTrap(int cpu) throws IOException {
        send(TRAP_HIGH_LOAD, "Перевантаження CPU: " + cpu + "%");
    }

    public void close() throws IOException {
        snmp.close();
    }
}