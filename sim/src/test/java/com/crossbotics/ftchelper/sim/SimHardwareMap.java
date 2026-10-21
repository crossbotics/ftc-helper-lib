package com.crossbotics.ftchelper.sim;

import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The robot configuration, simulated. The SDK's own lookup needs Android parts, so this one keeps
 * the devices in a plain list and answers the same questions the same way (and fails the same way:
 * an IllegalArgumentException for a name that is not there).
 */
final class SimHardwareMap extends HardwareMap {
    private final Map<String, HardwareDevice> byName = new LinkedHashMap<>();

    SimHardwareMap(Map<String, HardwareDevice> devices) {
        super(null, null);
        byName.putAll(devices);
        // The SDK's typed lists too, for code like hardwareMap.dcMotor.get("lift").
        for (Map.Entry<String, HardwareDevice> e : devices.entrySet()) {
            HardwareDevice d = e.getValue();
            String n = e.getKey();
            if (d instanceof com.qualcomm.robotcore.hardware.DcMotor) dcMotor.put(n, (com.qualcomm.robotcore.hardware.DcMotor) d);
            if (d instanceof com.qualcomm.robotcore.hardware.Servo) servo.put(n, (com.qualcomm.robotcore.hardware.Servo) d);
            if (d instanceof com.qualcomm.robotcore.hardware.CRServo) crservo.put(n, (com.qualcomm.robotcore.hardware.CRServo) d);
            if (d instanceof com.qualcomm.robotcore.hardware.TouchSensor) touchSensor.put(n, (com.qualcomm.robotcore.hardware.TouchSensor) d);
            if (d instanceof com.qualcomm.robotcore.hardware.DigitalChannel) digitalChannel.put(n, (com.qualcomm.robotcore.hardware.DigitalChannel) d);
            if (d instanceof com.qualcomm.robotcore.hardware.VoltageSensor) voltageSensor.put(n, (com.qualcomm.robotcore.hardware.VoltageSensor) d);
        }
    }

    @Override
    public <T> T get(Class<? extends T> type, String name) {
        T found = tryGet(type, name);
        if (found == null) {
            throw new IllegalArgumentException(String.format("Unable to find a hardware device with name \"%s\" and type %s",
                    name, type.getSimpleName()));
        }
        return found;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T tryGet(Class<? extends T> type, String name) {
        HardwareDevice d = byName.get(name);
        return type.isInstance(d) ? (T) d : null;
    }

    @Override
    public HardwareDevice get(String name) {
        HardwareDevice d = byName.get(name);
        if (d == null) throw new IllegalArgumentException("Unable to find a hardware device with the name \"" + name + "\"");
        return d;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getAll(Class<? extends T> type) {
        List<T> out = new ArrayList<>();
        for (HardwareDevice d : byName.values()) if (type.isInstance(d)) out.add((T) d);
        return out;
    }

    @Override
    public SortedSet<String> getAllNames(Class<? extends HardwareDevice> type) {
        SortedSet<String> out = new TreeSet<>();
        for (Map.Entry<String, HardwareDevice> e : byName.entrySet()) if (type.isInstance(e.getValue())) out.add(e.getKey());
        return out;
    }

    @Override
    public Set<String> getNamesOf(HardwareDevice device) {
        Set<String> out = new TreeSet<>();
        for (Map.Entry<String, HardwareDevice> e : byName.entrySet()) if (e.getValue() == device) out.add(e.getKey());
        return out;
    }

    @Override
    public int size() {
        return byName.size();
    }

    @Override
    public java.util.Iterator<HardwareDevice> iterator() {
        return Collections.unmodifiableCollection(byName.values()).iterator();
    }
}
