import org.apache.spark.sql.{SparkSession}
import org.apache.spark.rdd.{RDD}

object PageRank {
  def main(args: Array[String]) {
    val spark = SparkSession
      .builder
      .appName("SparkPageRank")
      .getOrCreate()

    val iters = 10
    val lines = spark.read.textFile(args(0)).rdd

    val graph = lines.map{ s =>
      val parts = s.split("\t")
      (parts(0), parts(1))
    }.distinct()

    val allNodes = graph.keys.union(graph.values).distinct()
    val n = allNodes.count()
    val dangle: RDD[(String, Double)] = allNodes.subtract(graph.keys).distinct().map(d => (d, 0.0)).persist()
    val dangleOut = dangle.map(l => (l._1, "OUT"))
    val allOut: RDD[(String, Double)] = allNodes.subtract(graph.values).distinct().map(l => (l, 0.0)).persist()
    val links = graph.union(dangleOut).distinct().groupByKey().cache()
    var ranks: RDD[(String, Double)] = links.mapValues(v => 1.0 / n).cache()

    for (i <- 1 to iters) {
      val contribs = links.join(ranks).values.flatMap{ case (urls, rank) =>
        val size = urls.size
        urls.filter(url => !url.equals("OUT")).map(url => (url, rank / size))
      }
      val con = contribs.union(allOut)
      val dangleRanks = ranks.join(dangle)
      val dangleFactors = dangleRanks.map(d => (d._1, d._2._1 + d._2._2))
      val dangleFactor = dangleFactors.values.sum() / n
      ranks = con.reduceByKey(_+_).mapValues(r => 0.15 / n + 0.85 * (r + dangleFactor))
    }

    val output = ranks.map(l => l._1 + "\t" + l._2.toString)
    output.saveAsTextFile("hdfs:///pagerank-output")
    spark.stop()
  }
}
