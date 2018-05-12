import java.io.IOException;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


public class InsertPhrase {
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

            for (int i=0; i<words.size(); i++) {
                prefix.set(String.join(" ", words.subList(0, i + 1)));
                if (i < words.size() - 1) {
                    suffix.set(String.join(" ", words.subList(i + 1, words.size())));
                } else {
                    suffix.set(EMPTY);
                }
                suffix.set(count + " " + suffix.toString());
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
            List<SuffixCount> suffixCounts = new ArrayList<>();
            Configuration conf = context.getConfiguration();
            final byte[] CF = Bytes.toBytes(conf.get("COLUMN_FAMILY"));

            float denom = 0;
            for (Text t : value) {
                List<String> suffixAndCount = new ArrayList<>(Arrays.asList(t.toString().split(" ")));
                Integer count = Integer.parseInt(suffixAndCount.get(0));
                String suffix = String.join(" ", suffixAndCount.subList(1, suffixAndCount.size()));
                suffixCounts.add(new SuffixCount(suffix, count));
                if (suffix.equals(EMPTY)) { denom = (float) count; }
            }

            List<SuffixCountList> scl = new ArrayList<>();
            scl.add(new SuffixCountList(3, 0.05f));
            scl.add(new SuffixCountList(2, 0.03f));
            scl.add(new SuffixCountList(2, 0.01f));
            scl.add(new SuffixCountList(1, 0.005f));

            for (SuffixCount sxc : suffixCounts) {
                if (!sxc.suffix.equals(EMPTY) && denom > 0) {
                    float num = (float) sxc.count;
                    float prob = num / denom;
                    sxc.setProb(prob);
                    int size = sxc.suffix.split(" ").length;
                    scl.get(size - 1).add(sxc);
                }
            }

            for (SuffixCountList sl : scl) {
                List<SuffixCount> sorted = sl.getSortedProbs();
                for (SuffixCount sxc : sorted) {
                    Put put = new Put(Bytes.toBytes(key.toString()));
                    put.addColumn(CF, Bytes.toBytes(sxc.suffix), Bytes.toBytes(String.valueOf(sxc.prob)));
                    context.write(null, put);
                }
            }
        }

        private class SuffixCountList {
            private List<SuffixCount> scl = new ArrayList<>();
            private int limit;
            private float probThresh;

            private SuffixCountList(int limit, float probThresh) {
                this.limit = limit;
                this.probThresh = probThresh;
            }

            private void add(SuffixCount sxc) {
                if (sxc.prob > probThresh) { scl.add(sxc); }
            }

            private List<SuffixCount> getSortedProbs() {
                List<SuffixCount> sorted = new ArrayList<>();
                scl.sort(new NgramProbComparator());
                int num;
                if (scl.size() >= limit) {
                    num = limit;
                } else {
                    num = scl.size();
                }
                for (int i=0; i<num; i++) {
                    sorted.add(scl.get(i));
                }
                return sorted;
            }
        }

        private class SuffixCount {
            private Integer count;
            private String suffix;
            private float prob;

            private SuffixCount(String suffix, Integer count) {
                this.suffix = suffix;
                this.count = count;
            }

            private void setProb(float prob) {
                this.prob = prob;
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
        Configuration conf = HBaseConfiguration.create();
        conf.set("hbase.master", zkAddr + ":14000");
        conf.set("hbase.zookeeper.quorum", zkAddr);
        conf.set("hbase.zookeeper.property.clientport", "2181");
        conf.set("COLUMN_FAMILY", columnFamily);
        Job job = Job.getInstance(conf, "hbase phrase predict");
        job.setJarByClass(InsertPhrase.class);
        job.setMapperClass(TokenizerMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        TableMapReduceUtil.initTableReducerJob(table, IntSumReducer.class, job);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}