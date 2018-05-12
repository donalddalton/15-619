import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapred.{FileSplit, InputSplit, TextInputFormat}
import org.apache.spark.rdd.{HadoopRDD, RDD}
import org.apache.spark.{SparkConf, SparkContext}

object DataFilter {
  // prefix blacklist (in lowercase)
  private val prefixBlacklist: Set[String] = Set("special:", "media:", "talk:", "user:", "user_talk:", "wikipedia:",
    "wikipedia_talk:", "file:", "timedtext:", "file_talk:", "timedtext_talk:", "mediawiki:", "mediawiki_talk:",
    "template:", "template_talk:", "help:", "help_talk:", "category:", "category_talk:", "portal:", "portal_talk:",
    "topic:", "book:", "book_talk:", "draft:", "draft_talk:", "module:", "gadget:", "module_talk:", "gadget_talk:",
    "education_program:", "gadget_definition:", "education_program_talk:", "gadget_definition_talk:")

  // filename extension blacklist (in lowercase) and disambiguation pages suffix
  private val filenameExtensionBlacklist: Set[String] = Set(".png", ".gif", ".jpg", ".jpeg", ".tiff", ".tif",
    ".xcf", ".mid", ".ogg", ".ogv", ".svg", ".djvu", ".oga", ".flac", ".opus", ".wav", ".webm", ".ico", ".txt",
    "_(disambiguation)")

  private val lowercase: Set[String] = Set("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o",
    "p", "q", "r", "s",  "t", "u", "v", "w", "x", "y", "z")

  // the special pages (case sensitive)
  private val specialPages: Set[String] = Set("404.php", "Main_Page", "-")

  def safeInt(s: String): Int = {
    try {
      s.toInt
    } catch {
      case e2: Exception => 0
    }
  }

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf()
    val sc = new SparkContext(conf)

    val fileRDD = sc.hadoopFile[LongWritable, Text, TextInputFormat](args(0))
    val hadoopRDD = fileRDD.asInstanceOf[HadoopRDD[LongWritable, Text]]

    val data: RDD[(Int, String)] = hadoopRDD
      .mapPartitionsWithInputSplit((inputSplit: InputSplit, iterator: Iterator[(LongWritable, Text)]) => {
        val filepath = inputSplit.asInstanceOf[FileSplit].getPath
        val splits = filepath.toString.split("-")
        val date = splits(splits.length - 2).substring(6, 8).toInt
        for (item <- iterator) yield (date, item._2.toString)
      })

    val split: RDD[(Int, Array[String])]          = data.mapValues(line => line.split(" ")).filter(line => line._2.length == 4 && (line._2(0) == "en" || line._2(0) == "en.m"))
    val decode: RDD[(Int, (String, Int))]         = split.mapValues(line => (PercentDecoder.decode(line(1)), safeInt(line(2))))
    val prefix: RDD[(Int, (String, Int))]         = decode.filter(line => !prefixBlacklist.exists(p => line._2._1.toLowerCase().startsWith(p)))
    val namespace: RDD[(Int, (String, Int))]      = prefix.filter(line => !lowercase.exists(p => line._2._1.startsWith(p)))
    val suffix: RDD[(Int, (String, Int))]         = namespace.filter(line => !filenameExtensionBlacklist.exists(f => line._2._1.toLowerCase().endsWith(f)))
    val special: RDD[(Int, (String, Int))]        = suffix.filter(line => !specialPages.exists(p => line._2._1.equals(p)))
    val dailyPairs: RDD[((String, Int), Int)]     = special.map(line => ((line._2._1, line._1), line._2._2))
    val dailyCounts: RDD[((String, Int), Int)]    = dailyPairs.reduceByKey(_+_)
    val dailyCountsMod: RDD[(String, (Int, Int))] = dailyCounts.map(line => (line._1._1, (line._1._2, line._2)))
    val gbk: RDD[(String, Map[Int, Int])]         = dailyCountsMod.groupByKey().mapValues(_.toMap)

    val out: RDD[(String, Array[Int])] = gbk.map(line => {
      var total = 0
      val counts: Array[Int] = new Array[Int](31)
      for (i <- 1 to 30) {
        if (line._2.contains(i)) {
          counts(i) = line._2(i)
          total = total + line._2(i)
        } else {
          counts(i) = 0
        }
      }
      counts(0) = total
      (line._1, counts)
    })

    val output = out.filter(line => line._2(0) > 100000)
      .map(line => {
        val buf = new StringBuilder
        val title = line._1
        val total = line._2(0)
        buf.append(total.toString).append("\t").append(title)
        for (i <- 1 to 30) {
          buf.append("\t").append(line._2(i))
        }
        buf
      })

    output.saveAsTextFile("hdfs:///filter-output")
    sc.stop()
  }
}
