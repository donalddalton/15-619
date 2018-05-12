import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.VIntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


public class NgramCount {
    public static class TokenizerMapper extends Mapper<Object, Text, Text, VIntWritable> {
        public enum CountersEnum { INPUT_WORDS }
        private final static VIntWritable one = new VIntWritable(1);
        private Text word = new Text();
        private static Pattern revisionPattern = Pattern.compile("<revision(.*?)>(.*?)</revision(.*?)>");
        private static Pattern textPattern = Pattern.compile("<text(.*?)>(.*?)</text(.*?)>");

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            StringBuilder sb = new StringBuilder();
            Matcher revisionMatcher = revisionPattern.matcher(value.toString());
            while (revisionMatcher.find()) {
                Matcher textMatcher = textPattern.matcher(revisionMatcher.group(0)
                        .replaceAll("</?revision(.*?)>", "")
                        .replaceAll("</revision(.*?)>", "")
                );

                while (textMatcher.find()) {
                    String text = textMatcher.group(0)
                            .replaceAll("</?text(.*?)>", "");

                    if (!text.trim().equals("")) {
                        String clean = StringEscapeUtils.unescapeHtml(text.toLowerCase())
                                .replaceAll("(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]", " ")
                                .replaceAll("</?ref(.*?)>", " ")
                                .replaceAll("'+(?=[^a-zA-Z])|(?<=[^a-zA-Z])'+", " ")
                                .replaceAll("[^a-zA-Z']", " ")
                                .replaceAll("\\s+", " ");
                        sb.append(clean.trim()).append(" ");
                    }
                }
            }

            List<String> cleanWords = new ArrayList<>(Arrays.asList(sb.toString().trim().split(" ")));
            StringBuilder sb2 = new StringBuilder();
            if (cleanWords.size() > 4) {
                for (int i=1; i<6; i++) {
                    for (int j=0; j < cleanWords.size()-i+1; j++) {
                        String s = String.join(" ", cleanWords.subList(j, i+j));
                        sb2.append(s).append(",");
                    }
                }
            } else {
                for (int i=1; i < cleanWords.size(); i++) {
                    for (int j=0; j < cleanWords.size()-i+1; j++) {
                        String s = String.join(" ", cleanWords.subList(j, i+j));
                        sb2.append(s).append(",");
                    }
                }
            }

            if (sb2.length() > 0) {
                sb2.deleteCharAt(sb2.length() - 1);
                StringTokenizer itr = new StringTokenizer(sb2.toString().trim(), ",");
                while (itr.hasMoreTokens()) {
                    word.set(itr.nextToken());
                    context.write(word, one);
                    Counter counter = context.getCounter(NgramCount.TokenizerMapper.CountersEnum.class.getName(), NgramCount.TokenizerMapper.CountersEnum.INPUT_WORDS.toString());
                    counter.increment(1);
                }
            }
        }
    }

    public static class IntSumReducer extends Reducer<Text, VIntWritable, Text, VIntWritable> {
        private VIntWritable result = new VIntWritable();
        public void reduce(Text key, Iterable<VIntWritable> values, Context context) throws IOException, InterruptedException {
            int count = 0;
            for (VIntWritable val : values) {
                count += val.get();
            }
            result.set(count);
            context.write(key, result);
        }
    }

    public static class IntSumCombiner extends Reducer<Text, VIntWritable, Text, VIntWritable> {
        private VIntWritable result = new VIntWritable();
        public void reduce(Text key, Iterable<VIntWritable> values, Context context) throws IOException, InterruptedException {
            int count = 0;
            for (VIntWritable val : values) {
                count += val.get();
            }
            result.set(count);
            if (result.get() > 2) {
                context.write(key, result);
            }
        }
    }


    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "word count");
        job.setJarByClass(NgramCount.class);
        job.setMapperClass(TokenizerMapper.class);
        job.setCombinerClass(IntSumCombiner.class);
        job.setReducerClass(IntSumReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(VIntWritable.class);
        // job.setNumReduceTasks(1);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}