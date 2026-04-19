import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

/**
 * Простий SNMP-клієнт для перевірки агента.
 */
public class SnmpTester {

    public String get(String agentAddress, String oid) {
        try {
            TransportMapping<?> transport = new DefaultUdpTransportMapping();
            Snmp snmp = new Snmp(transport);
            transport.listen();

            CommunityTarget<UdpAddress> target = new CommunityTarget<>();
            target.setAddress(new UdpAddress(agentAddress));
            target.setCommunity(new OctetString("public"));
            target.setVersion(SnmpConstants.version2c);
            target.setTimeout(2000);
            target.setRetries(1);

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            ResponseEvent<?> event = snmp.send(pdu, target);
            snmp.close();

            if (event != null && event.getResponse() != null) {
                return event.getResponse().get(0).getVariable().toString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }


    public static void main(String[] args) throws Exception {

        // Адреса агента — той самий порт що в Main.java
        String agentAddress = "localhost/16100";

        System.out.println("Підключаємось до агента: " + agentAddress);

        // Відкриваємо UDP транспорт
        TransportMapping<?> transport = new DefaultUdpTransportMapping();
        Snmp snmp = new Snmp(transport);
        transport.listen();

        // Налаштовуємо ціль (наш агент)
        CommunityTarget<UdpAddress> target = new CommunityTarget<>();
        target.setAddress(new UdpAddress(agentAddress));
        target.setCommunity(new OctetString("public"));
        target.setVersion(SnmpConstants.version2c);
        target.setTimeout(3000);
        target.setRetries(1);

        // Список OID-ів які хочемо запитати
        String[][] oids = {
                {"1.3.6.1.2.1.1.1.0", "sysDescr       "},
                {"1.3.6.1.2.1.1.5.0", "sysName        "},
                {"1.3.6.1.2.1.2.2.1.14.1", "ifInErrors     "},
                {"1.3.6.1.4.1.99999.1.0", "encryptionStatus"},
                {"1.3.6.1.4.1.99999.2.0", "SNR (dB)       "},
                {"1.3.6.1.4.1.99999.3.0", "temperature (C)"},
        };

        System.out.println("\n── Відповідь від агента ──────────────────────");

        for (String[] oid : oids) {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid[0])));
            pdu.setType(PDU.GET);

            ResponseEvent<?> event = snmp.send(pdu, target);

            if (event != null && event.getResponse() != null) {
                Variable value = event.getResponse().get(0).getVariable();
                System.out.printf("  %-18s = %s%n", oid[1], value);
            } else {
                System.out.printf("  %-18s = TIMEOUT (агент не відповідає)%n", oid[1]);
            }
        }

        System.out.println("──────────────────────────────────────────────");
        snmp.close();
    }
}
