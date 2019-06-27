package com.example.beaconpoc.beaconpoc;

public class BeaconInfo {


    private String uuid;
    private double distance;

    private int major;

    private int minor;
    private String name;
    private String timeFound;

    public BeaconInfo(String uuid, int major ,int minor,  double distance, String name , String timeFound) {
        this.uuid = uuid;
        this.distance = distance;
        this.major = major;
        this.minor = minor;
        this.name = name;
        this.timeFound = timeFound;
    }

    public String getName(){
        return this.name;
    }
    public String getUuid() {
        return uuid;
    }
    public double getDistance() {
        return distance;
    }
    public int getMajor() {
        return major;
    }
    public int getMinor() {
        return minor;
    }
    public String getTimeFound() {return timeFound;}


}
