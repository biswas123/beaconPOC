package com.example.beaconpoc.beaconpoc;

public class BeaconInfo {


    private String uuid;
    private double distance;

    private int major;

    private int minor;

    public BeaconInfo(String uuid, int major ,int minor,  double distance) {
        this.uuid = uuid;
        this.distance = distance;
        this.major = major;
        this.minor = minor;
    }


    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public int getMajor() {
        return major;
    }

    public void setMajor(int major) {
        this.major = major;
    }

    public int getMinor() {
        return minor;
    }

    public void setMinor(int minor) {
        this.minor = minor;
    }


}
