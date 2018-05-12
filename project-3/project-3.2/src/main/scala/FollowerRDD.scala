import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SparkSession

object FollowerRDD {
  def main(args: Array[String]) {

    val conf = new SparkConf()
    val sc = new SparkContext(conf)

    val spark = SparkSession
      .builder
      .appName("SparkPageRank")
      .getOrCreate()

    val lines = spark.read.textFile(args(0)).rdd

    val graph = lines.map{ s =>
      val parts = s.split("\t")
      (parts(1), parts(0))
    }

    val countsByFollower = graph.groupByKey().map(l => {
      (l._2.size, l._1)
    })

    val sorted = countsByFollower.sortByKey(ascending = false).collect().slice(0, 100)
    val output = sc.parallelize(sorted).map(l => l._2 + "\t" + l._1.toString)

    output.saveAsTextFile("hdfs:///followerRDD-output")
  }
}
