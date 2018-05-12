import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.json.JSONObject;
import org.apache.kafka.clients.producer.*;

public class DataProducer {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", args[0] + ":9092");  // EMR master node ip
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Producer<String, String> producer = new KafkaProducer<>(props);
        BufferedReader reader = null;
        try {
            File file = new File(args[1]);
            reader = new BufferedReader(new FileReader(file));
            Map<String, String> hm = new HashMap<>();
            hm.put("DRIVER_LOCATION", "driver-locations");

            hm.put("LEAVING_BLOCK", "events");
            hm.put("ENTERING_BLOCK", "events");
            hm.put("RIDE_REQUEST", "events");
            hm.put("RIDE_COMPLETE", "events");

            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject obj = new JSONObject(line);
                if (!obj.has("type") || !obj.has("blockId")) { continue; }
                Integer blockId = obj.getInt("blockId");
                Integer destination =  blockId % 5;
                String type = obj.getString("type");
                if (hm.containsKey(type)) {
                    producer.send(new ProducerRecord<>(hm.get(type), destination, null, line));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            producer.close();
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        producer.close();
    }
}
