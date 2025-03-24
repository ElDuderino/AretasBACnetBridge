package com.pyropath.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.obj.AnalogInputObject;
import com.serotonin.bacnet4j.obj.BinaryInputObject;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.Polarity;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
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
        localDevice = new LocalDevice(DEVICE_ID, transport);
        localDevice.initialize();

        setupObjects();
        startPolling();

        System.out.println("BACnet IoT Gateway running...");
        localDevice.sendGlobalBroadcast(new WhoIsRequest());
    }

    private static void setupObjects() throws BACnetException {
        List<Map<String, Object>> sensors = getSensors();

        for (Map<String, Object> sensor : sensors) {
            int id = (Integer) sensor.get("id");
            String name = (String) sensor.get("name");
            String type = (String) sensor.get("type");

            if ("AI".equals(type)) {
                //public AnalogInputObject(LocalDevice localDevice, int instanceNumber, String name, float presentValue, EngineeringUnits units, boolean outOfService)
                EngineeringUnits unit = EngineeringUnits.partsPerMillion;
                AnalogInputObject ai;
                try {

                    ai = new AnalogInputObject(localDevice, id, name, 0.0f, unit, false);
                    localDevice.addObject(ai);
                    analogInputs.put(id, ai);

                } catch (BACnetServiceException ex) {
                    Logger.getLogger(BacnetIoTGateway.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else if ("BI".equals(type)) {
                // public BinaryInputObject(LocalDevice localDevice, int instanceNumber, String name, BinaryPV presentValue, boolean outOfService, Polarity polarity)
                BinaryPV presentValue = BinaryPV.active;
                Polarity polarity = Polarity.normal;
                BinaryInputObject bi;
                try {
                    bi = new BinaryInputObject(localDevice, id, name, presentValue, false, polarity);
                    localDevice.addObject(bi);
                    binaryInputs.put(id, bi);
                } catch (BACnetServiceException ex) {
                    Logger.getLogger(BacnetIoTGateway.class.getName()).log(Level.SEVERE, null, ex);
                }

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
                    
                    //analogInputs.get(id).setPresentValue((float) value);
                    analogInputs.get(id).writePropertyInternal(PropertyIdentifier.presentValue, new Real((float)value));
                    System.out.println("Updated " + analogInputs.get(id).getObjectName() + " to " + value);
                } else if (binaryInputs.containsKey(id)) {
                    
                    //binaryInputs.get(id).setPresentValue((boolean) value);
                    BinaryPV pValue;
                    if((boolean)value){
                        pValue = BinaryPV.active;
                    }else{
                        pValue = BinaryPV.inactive;
                    }
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
