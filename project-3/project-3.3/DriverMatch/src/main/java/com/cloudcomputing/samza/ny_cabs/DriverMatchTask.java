package com.cloudcomputing.samza.ny_cabs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.samza.config.Config;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.task.InitableTask;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.StreamTask;
import org.apache.samza.task.TaskContext;
import org.apache.samza.task.TaskCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Consumes the stream of driver location updates and rider cab requests.
 * Outputs a stream which joins these 2 streams and gives a stream of rider to
 * driver matches.
 */
public class DriverMatchTask implements StreamTask, InitableTask {
    static Logger LOGGER = LoggerFactory.getLogger(DriverMatchTask.class);
    private KeyValueStore<Integer, Map<Integer, Object>> locations;

    private static final double MAX_MONEY      = 100.0;
    private static final String LEAVING_BLOCK  = "LEAVING_BLOCK";
    private static final String ENTERING_BLOCK = "ENTERING_BLOCK";
    private static final String RIDE_REQUEST   = "RIDE_REQUEST";
    private static final String RIDE_COMPLETE  = "RIDE_COMPLETE";
    private static final String AVAILABLE      = "AVAILABLE";

    @Override
    @SuppressWarnings("unchecked")
    public void init(Config config, TaskContext context) throws Exception {
        locations = (KeyValueStore<Integer, Map<Integer, Object>>) context.getStore("driver-loc");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(IncomingMessageEnvelope envelope, MessageCollector collector, TaskCoordinator coordinator) {
        String incomingStream = envelope.getSystemStreamPartition().getStream();
        Map<String, Object> message = (Map<String, Object>) envelope.getMessage();

        String type = (String) message.get("type");
        if (type == null) { return; }

        Integer blockId = (Integer) message.get("blockId");
        // create the driver map for given blockId if it doesn't exist
        if (locations.get(blockId) == null) {
            Map<Integer, Object> hm = new HashMap<>();
            locations.put(blockId, hm);
        }

        if (incomingStream.equals(DriverMatchConfig.DRIVER_LOC_STREAM.getStream())) {
            // driver location updates
            processLocationEvent((Map<String, Object>) envelope.getMessage());
        } else if (incomingStream.equals(DriverMatchConfig.EVENT_STREAM.getStream())) {
	        // event updates
            if (type.equals(LEAVING_BLOCK)) {
                processLeavingBlockEvent((Map<String, Object>) envelope.getMessage());
            } else if (type.equals(ENTERING_BLOCK)) {
                processEnteringBlockEvent((Map<String, Object>) envelope.getMessage());
            } else if (type.equals(RIDE_COMPLETE)) {
                processRideCompleteEvent((Map<String, Object>) envelope.getMessage());
            } else if (type.equals(RIDE_REQUEST)) {
                processRideRequestEvent((Map<String, Object>) envelope.getMessage(), collector);
            } else {
                throw new IllegalStateException("Unexpected input stream: " + envelope.getSystemStreamPartition());
            }
        } else {
            throw new IllegalStateException("Unexpected input stream: " + envelope.getSystemStreamPartition());
        }
    }

    @SuppressWarnings("unchecked")
    private void processRideRequestEvent(Map<String, Object> message, MessageCollector collector) {
        Integer blockId         = (Integer) message.get("blockId");
        Integer clientId        = (Integer) message.get("clientId");
        Double clientLatitude   = (Double)  message.get("latitude");
        Double clientLongitude  = (Double)  message.get("longitude");
        String clientGenderPref = (String)  message.get("gender_preference");

        Map<Integer, Object> drivers = locations.get(blockId);
        List<Driver> driverList = new ArrayList<>();
        for (Object o : drivers.values()) {
            String json = (String) o;
            if (isMalformed(json, "processRideRequestEvent")) { return; }

            // deserialize the driver string
            Gson gson = new Gson();
            Map<String, Object> map = new HashMap<>();
            map = (Map<String, Object>) gson.fromJson(json, map.getClass());

            // get the driver info
            Double driverId  = (Double)  map.get("driverId");
            Double drivBid   = (Double)  map.get("blockId");
            Double latitude  = (Double)  map.get("latitude");
            Double longitude = (Double)  map.get("longitude");
            Double rating    = (Double)  map.get("rating");
            Double salary    = (Double)  map.get("salary");
            String gender    = (String)  map.get("gender");
            String status    = (String)  map.get("status");

            // set the driver info
            Driver d = new Driver();
            d.setDriverId(driverId);
            d.setBlockId(drivBid);
            d.setLatitude(latitude);
            d.setLongitude(longitude);
            d.setRating(rating);
            d.setSalary(salary);
            d.setGender(gender);
            d.setStatus(status);
            Double matchScore = d.matchScore(clientLatitude, clientLongitude, clientGenderPref);
            d.setScore(matchScore);

            // if the driver is in same blockId and available then add to candidate match pool
            if (status.equals(AVAILABLE) && blockId.equals(drivBid.intValue())) {
                driverList.add(d);
                LOGGER.debug("<<< CANDIDATE DRIVER FOUND: " + d.toString());
            }
        }

        // return if there are no matches
        if (driverList.size() == 0) { return; }

        // sort by match score in descending order
        driverList.sort((o1, o2) -> o2.getScore().compareTo(o1.getScore()));

        Driver match = null;
        for (Driver driver : driverList) {
            // pick the first driver with salary < MAX_MONEY
            if (driver.getSalary() < MAX_MONEY) {
                match = driver;
                LOGGER.debug("<<< DRIVER MATCH FOUND: " + match.toString());
                break;
            }
        }

        // if there are no drivers with salary < MAX_MONEY then pick driver with highest match score
        if (match == null) {
            match = driverList.get(0);
            LOGGER.debug("<< NO MATCH UNDER SALARY CAP, SELECTING DRIVER: " + match.toString());
        }

        // write the driver-client to the match-stream
        Map<String, Object> result = new HashMap<>();
        result.put("driverId", match.getDriverId().intValue());
        result.put("clientId", clientId);
        collector.send(new OutgoingMessageEnvelope(DriverMatchConfig.MATCH_STREAM, result));
        LOGGER.debug("<<< MATCH SUCCESS " + result.toString());

        // remove the driver from the blockId driver pool
        locations.get(blockId).remove(match.getDriverId().intValue());

        LOGGER.debug("<<< REMOVING MATCHED DRIVER FROM POOL: " +
                locations.get(blockId).containsKey(match.getDriverId().intValue()));
    }

    private void processEnteringBlockEvent(Map<String, Object> message) {
        Integer driverId = (Integer) message.get("driverId");
        Integer blockId  = (Integer) message.get("blockId");
        Double latitude  = (Double)  message.get("latitude");
        Double longitude = (Double)  message.get("longitude");
        Double rating    = (Double)  message.get("rating");
        Integer salary   = (Integer) message.get("salary");
        String gender    = (String)  message.get("gender");
        String status    = (String)  message.get("status");

        // add the driver back to the blockId driver pool
        Map<String, Object> driver = new HashMap<>();
        driver.put("driverId", driverId);
        driver.put("blockId", blockId);
        driver.put("latitude", latitude);
        driver.put("longitude", longitude);
        driver.put("gender", gender);
        driver.put("rating", rating);
        driver.put("salary", salary);
        driver.put("status", status);
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(driver);
        if (isMalformed(json, "processEnteringBlockEvent")) { return; }
        locations.get(blockId).put(driverId, json);

        LOGGER.debug("<<< DRIVER ENTERING BLOCK: " + json);
    }

    @SuppressWarnings("unchecked")
    private void processLeavingBlockEvent(Map<String, Object> message) {
        Integer driverId = (Integer) message.get("driverId");
        Integer blockId  = (Integer) message.get("blockId");
        Double latitude  = (Double)  message.get("latitude");
        Double longitude = (Double)  message.get("longitude");
        String status    = (String)  message.get("status");

        String driver = (String) locations.get(blockId).get(driverId);
        if (isMalformed(driver, "processLeavingBlockEvent")) { return; }

        // remove the driver from the blockId driver pool
        Gson gson = new Gson();
        Map<String, Object> map = new HashMap<>();
        map = (Map<String, Object>) gson.fromJson(driver, map.getClass());
        map.put("blockId", blockId);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("status", status);
        locations.get(blockId).remove(driverId);
        LOGGER.debug("<<< DRIVER LEAVING BLOCK, REMOVING FROM BLOCK POOL " + map.toString());
    }

    @SuppressWarnings("unchecked")
    private void processLocationEvent(Map<String, Object> message) {
        Integer driverId = (Integer) message.get("driverId");
        Integer blockId = (Integer) message.get("blockId");
        Double latitude = (Double) message.get("latitude");
        Double longitude = (Double) message.get("longitude");

        String driver = (String) locations.get(blockId).get(driverId);
        if (isMalformed(driver, "processLocationEvent")) { return; }

        // update driver location
        Map<String, Object> map = new HashMap<>();
        Gson gson = new Gson();
        map = (Map<String, Object>) gson.fromJson(driver, map.getClass());
        map.put("blockId", blockId);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        Gson gsonBuilder = new GsonBuilder().create();
        String json = gsonBuilder.toJson(map);
        locations.get(blockId).put(driverId, json);

        LOGGER.debug("<<< DRIVER LOCATION UPDATE: " + json);
    }

    @SuppressWarnings("unchecked")
    private void processRideCompleteEvent(Map<String, Object> message) {
        Integer driverId     = (Integer) message.get("driverId");
        Integer blockId      = (Integer) message.get("blockId");
        Double latitude      = (Double)  message.get("latitude");
        Double longitude     = (Double)  message.get("longitude");
        Double rating        = (Double)  message.get("rating");
        Double userRating    = (Double)  message.get("user_rating");
        Integer salary       = (Integer)  message.get("salary");
        String gender        = (String)  message.get("gender");

        String driver = (String) locations.get(blockId).get(driverId);
        if (isMalformed(driver, "processRideCompleteEvent")) { return; }

        // add the driver back to the blockId driver pool
        Map<String, Object> map = new HashMap<>();
        Gson gson = new Gson();
        map = (Map<String, Object>) gson.fromJson(driver, map.getClass());
        map.put("driverId", driverId);
        map.put("blockId", blockId);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("gender", gender);
        map.put("rating", rating);
        map.put("salary", salary + 1);
        map.put("status", AVAILABLE);
        map.put("rating", ((Double) map.get("rating") + userRating) / 2);
        Gson gsonBuilder = new GsonBuilder().create();
        String json = gsonBuilder.toJson(map);
        locations.get(blockId).put(driverId, json);

        LOGGER.debug("<<< FINISHED RIDE UPDATE: " + map.toString());
    }

    @SuppressWarnings("unchecked")
    public boolean isMalformed(String driver, String event) {
        boolean state = false;
        if (driver == null) {
            state = true;
        } else {
            Map<String, Object> map = new HashMap<>();
            Gson gson = new Gson();
            map = (Map<String, Object>) gson.fromJson(driver, map.getClass());
            if (map.get("driverId")  == null) { state = true; }
            if (map.get("blockId")   == null) { state = true; }
            if (map.get("latitude")  == null) { state = true; }
            if (map.get("longitude") == null) { state = true; }
            if (map.get("rating")    == null) { state = true; }
            if (map.get("salary")    == null) { state = true; }
            if (map.get("gender")    == null) { state = true; }
            if (map.get("status")    == null) { state = true; }
        }

        LOGGER.debug("<<< CHECKING FOR MALFORMED DRIVER: " + driver + "ON EVENT: " + event);

        return state;
    }
}


