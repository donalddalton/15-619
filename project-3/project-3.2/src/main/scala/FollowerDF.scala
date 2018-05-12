import org.apache.spark.sql.SparkSession

object FollowerDF {
  def main(args: Array[String]) {
    val spark = SparkSession.builder().appName("Graph").getOrCreate()

    val graph = spark.read.format("com.databricks.spark.csv").option("delimiter", "\t")
      .load(args(0))
      .toDF(Seq("u", "v"): _*)

    graph.createOrReplaceTempView("graph")

    // sorted by followers
    val countsByFollower = spark.sql("select count(u), v from graph group by v order by count(u) desc limit 100")
      .toDF(Seq("count", "followee"): _*)
      .select(Array("followee", "count").head, Array("followee", "count").tail: _*)

    countsByFollower.coalesce(1).write.format("parquet").save("hdfs:///followerDF-output")
  }
}