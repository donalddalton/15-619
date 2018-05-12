package com.cloudcomputing.samza.ny_cabs;

import org.apache.samza.system.SystemStream;

public class DriverMatchConfig {
    public static final SystemStream DRIVER_LOC_STREAM = new SystemStream("kafka", "driver-locations");
    public static final SystemStream EVENT_STREAM = new SystemStream("kafka", "events");
    public static final SystemStream MATCH_STREAM = new SystemStream("kafka", "match-stream");
}
