import java.io.IOException;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;


public class InsertWord {
    private static final String EMPTY = "EMPTY";

    public static class TokenizerMapper extends Mapper<Object, Text, Text, Text> {
        private static final Logger LOGGER = Logger.getLogger(TokenizerMapper.class);
        private Text prefix = new Text();
        private Text suffix = new Text();

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            LOGGER.setLevel(Level.WARN);
        }

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            List<String> line = new ArrayList<>(Arrays.asList(value.toString().split("\t")));
            List<String> words = new ArrayList<>(Arrays.asList(line.get(0).split(" ")));
            String count = line.get(1);

            prefix.set(line.get(0));
            suffix.set(EMPTY + " " + count);
            context.write(prefix, suffix);

            if (words.size() > 1) {
                suffix.set(words.get(words.size() - 1) + " " + count);
                words.remove(words.size() - 1);
                prefix.set(String.join(" ", words));
                context.write(prefix, suffix);
            }
        }
    }

    public static class IntSumReducer extends TableReducer<Text, Text, ImmutableBytesWritable> {
        private static final Logger LOGGER = Logger.getLogger(IntSumReducer.class);

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            LOGGER.setLevel(Level.WARN);
        }

        public void reduce(Text key, Iterable<Text> value, Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            final byte[] CF = Bytes.toBytes(conf.get("COLUMN_FAMILY"));
            final int n = Integer.parseInt(conf.get("n"));
            List<SuffixCount> suffixCounts = new ArrayList<>();

            float denom = 0;
            for (Text t : value) {
                List<String> suffixAndCount = new ArrayList<>(Arrays.asList(t.toString().split(" ")));
                String suffix = suffixAndCount.get(0);
                Integer count = Integer.parseInt(suffixAndCount.get(1));
                suffixCounts.add(new SuffixCount(suffix, count));
                if (suffix.equals(EMPTY)) { denom = (float) count; }
            }

            suffixCounts.sort(new NgramProbComparator());
            if (suffixCounts.size() < n) {
                for (SuffixCount sxc : suffixCounts) {
                    if (!sxc.suffix.equals(EMPTY) && denom > 0) {
                        float num = (float) sxc.count;
                        float prob = num / denom;
                        String probability = String.valueOf(prob);
                        Put put = new Put(Bytes.toBytes(key.toString()));
                        put.addColumn(CF, Bytes.toBytes(sxc.suffix), Bytes.toBytes(probability));
                        context.write(null, put);
                    }
                }
            } else {
                int inserts = 0;
                for (int idx=0; idx<suffixCounts.size(); idx++) {
                    SuffixCount sxc = suffixCounts.get(idx);
                    if (!sxc.suffix.equals(EMPTY) && denom > 0) {
                        float num = (float) sxc.count;
                        float prob = num / denom;

                        String probability = String.valueOf(prob);
                        Put put = new Put(Bytes.toBytes(key.toString()));
                        put.addColumn(CF, Bytes.toBytes(sxc.suffix), Bytes.toBytes(probability));
                        context.write(null, put);
                        inserts++;
                    }
                    if (inserts == n) {
                        break;
                    }
                }
            }
        }

        private class SuffixCount {
            private Integer count;
            private String suffix;

            private SuffixCount(String suffix, Integer count) {
                this.suffix = suffix;
                this.count = count;
            }
        }

        public class NgramProbComparator implements Comparator<SuffixCount> {
            @Override public int compare(SuffixCount o1, SuffixCount o2) {
                if (o1.count.equals(o2.count)) {
                    return o1.suffix.compareTo(o2.suffix);
                } else {
                    return o2.count.compareTo(o1.count);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String zkAddr = args[2];
        String table = args[3];
        String columnFamily = args[4];
        String n = args[5];
        Configuration conf = HBaseConfiguration.create();
        conf.set("hbase.master", zkAddr + ":14000");
        conf.set("hbase.zookeeper.quorum", zkAddr);
        conf.set("hbase.zookeeper.property.clientport", "2181");
        conf.set("COLUMN_FAMILY", columnFamily);
        conf.set("n", n);
        Job job = Job.getInstance(conf, "hbase word predict");
        job.setJarByClass(InsertWord.class);
        job.setMapperClass(TokenizerMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        TableMapReduceUtil.initTableReducerJob(table, IntSumReducer.class, job);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}