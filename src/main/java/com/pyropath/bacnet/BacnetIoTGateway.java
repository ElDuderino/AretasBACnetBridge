package com.pyropath.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.obj.AnalogInputObject;
import com.serotonin.bacnet4j.obj.BinaryInputObject;
import com.serotonin.bacnet4j.obj.DeviceObject;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.Polarity;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BacnetIoTGateway {

    private static final int DEVICE_ID = 1234;
    private static final int PORT = 0xBAC0; // Default BACnet port (47808)
    private static LocalDevice localDevice;
    private static Map<Integer, AnalogInputObject> analogInputs = new HashMap<>();
    private static Map<Integer, BinaryInputObject> binaryInputs = new HashMap<>();

    public static void main(String[] args) throws Exception {
        IpNetwork network = new IpNetworkBuilder()
                .withPort(PORT)
                .withLocalBindAddress("10.0.0.232")
                .withSubnet("10.0.0.0", 24)
                .build();

        DefaultTransport transport = new DefaultTransport(network);

        // DeviceObject is automatically created in the LocalDevice constructor.
        localDevice = new LocalDevice(DEVICE_ID, transport);
        localDevice.initialize();

        // Create sensor objects if not already present.
        setupObjects();
        startPolling();

        System.out.println("BACnet IoT Gateway running...");
        localDevice.sendGlobalBroadcast(new WhoIsRequest());

        // Add clean shutdown:
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                localDevice.terminate();
                System.out.println("BACnet local device terminated.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    private static void setupObjects() {
        List<Map<String, Object>> sensors = getSensors();

        for (Map<String, Object> sensor : sensors) {
            int id = (Integer) sensor.get("id");
            String name = (String) sensor.get("name");
            String type = (String) sensor.get("type");

            // Create an object identifier based on type and id.
            ObjectIdentifier oid = new ObjectIdentifier(
                    "AI".equals(type) ? ObjectType.analogInput : ObjectType.binaryInput,
                    id
            );

            // Check if the object is already registered in the LocalDevice.
            if (localDevice.getObject(oid) != null) {
                Logger.getLogger(BacnetIoTGateway.class.getName()).log(Level.WARNING,
                        "Object with ID {0} already exists. Using the existing object.", id);
                // Update our maps so we can later update the value.
                if ("AI".equals(type)) {
                    analogInputs.put(id, (AnalogInputObject) localDevice.getObject(oid));
                } else if ("BI".equals(type)) {
                    binaryInputs.put(id, (BinaryInputObject) localDevice.getObject(oid));
                }
                continue;
            }

            try {
                // Instantiate the sensor object.
                // The constructors of AnalogInputObject and BinaryInputObject auto-register the object
                // with the LocalDevice, so there's no need to call localDevice.addObject() explicitly.
                if ("AI".equals(type)) {
                    AnalogInputObject ai = new AnalogInputObject(localDevice, id, name, 0.0f, EngineeringUnits.partsPerMillion, false);
                    analogInputs.put(id, ai);
                } else if ("BI".equals(type)) {
                    BinaryInputObject bi = new BinaryInputObject(localDevice, id, name, BinaryPV.inactive, false, Polarity.normal);
                    binaryInputs.put(id, bi);
                }
            } catch (Exception ex) {
                Logger.getLogger(BacnetIoTGateway.class.getName()).log(Level.SEVERE, "Could not create sensor object: " + id, ex);
            }
        }
    }

    private static void startPolling() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            List<Map<String, Object>> sensors = getSensors();
            for (Map<String, Object> sensor : sensors) {
                int id = (Integer) sensor.get("id");
                Object value = getSensorData(id);

                if (analogInputs.containsKey(id)) {
                    analogInputs.get(id).writePropertyInternal(PropertyIdentifier.presentValue, new Real((float) value));
                    System.out.println("Updated " + analogInputs.get(id).getObjectName() + " to " + value);
                } else if (binaryInputs.containsKey(id)) {
                    BinaryPV pValue = ((boolean) value) ? BinaryPV.active : BinaryPV.inactive;
                    binaryInputs.get(id).writePropertyInternal(PropertyIdentifier.presentValue, pValue);
                    System.out.println("Updated " + binaryInputs.get(id).getObjectName() + " to " + value);
                }
            }
        }, 0, 2, TimeUnit.MINUTES);
    }

    private static List<Map<String, Object>> getSensors() {
        return Arrays.asList(
                sensor(1001, "Room 101 Temp", "AI"),
                sensor(1002, "Room 102 CO2", "AI"),
                sensor(1003, "Room 103 Motion", "BI")
        );
    }

    private static Object getSensorData(int sensorId) {
        Random random = new Random();
        switch (sensorId) {
            case 1001:
                return 20.0f + random.nextFloat() * 5.0f;
            case 1002:
                return 400f + random.nextFloat() * 400f;
            case 1003:
                return random.nextBoolean();
            default:
                return 0;
        }
    }

    private static Map<String, Object> sensor(int id, String name, String type) {
        Map<String, Object> sensor = new HashMap<>();
        sensor.put("id", id);
        sensor.put("name", name);
        sensor.put("type", type);
        return sensor;
    }
}
