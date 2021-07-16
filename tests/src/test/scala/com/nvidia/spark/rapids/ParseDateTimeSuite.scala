/*
 * Copyright (c) 2020-2021, NVIDIA CORPORATION.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nvidia.spark.rapids

import ai.rapids.cudf.ColumnVector
import java.sql.{Date, Timestamp}
import org.scalatest.BeforeAndAfterEach
import scala.collection.mutable.ListBuffer

import org.apache.spark.SparkConf
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.functions.{col, to_date, to_timestamp, unix_timestamp}
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.rapids.GpuToTimestamp.{FIX_DATES, FIX_SINGLE_DIGIT_DAY, FIX_SINGLE_DIGIT_HOUR, FIX_SINGLE_DIGIT_MINUTE, FIX_SINGLE_DIGIT_MONTH, FIX_SINGLE_DIGIT_SECOND, FIX_TIMESTAMPS, REMOVE_WHITESPACE_FROM_MONTH_DAY}
import org.apache.spark.sql.rapids.RegexReplace

class ParseDateTimeSuite extends SparkQueryCompareTestSuite with BeforeAndAfterEach {

  private val CORRECTED_TIME_PARSER_POLICY: SparkConf = new SparkConf()
    .set(SQLConf.LEGACY_TIME_PARSER_POLICY.key, "CORRECTED")

  private val LEGACY_TIME_PARSER_POLICY_CONF: SparkConf = new SparkConf()
    .set(SQLConf.LEGACY_TIME_PARSER_POLICY.key, "LEGACY")
    .set(RapidsConf.INCOMPATIBLE_DATE_FORMATS.key, "true")

  override def beforeEach() {
    GpuOverrides.removeAllListeners()
  }

  override def afterEach() {
    GpuOverrides.removeAllListeners()
  }

  testSparkResultsAreEqual("to_date dd/MM/yy (fall back)",
    datesAsStrings,
    conf = new SparkConf().set(SQLConf.LEGACY_TIME_PARSER_POLICY.key, "CORRECTED")
        .set(RapidsConf.INCOMPATIBLE_DATE_FORMATS.key, "true")
        // until we fix https://github.com/NVIDIA/spark-rapids/issues/2118 we need to fall
        // back to CPU when parsing two-digit years
        .set(RapidsConf.TEST_ALLOWED_NONGPU.key,
          "ProjectExec,Alias,Cast,GetTimestamp,UnixTimestamp,Literal,ShuffleExchangeExec")) {
    df => df.withColumn("c1", to_date(col("c0"), "dd/MM/yy"))
  }

  testSparkResultsAreEqual("to_date yyyy-MM-dd",
      datesAsStrings,
      conf = CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", to_date(col("c0"), "yyyy-MM-dd"))
  }

  testSparkResultsAreEqual("to_date yyyy-MM-dd LEGACY",
    datesAsStrings,
    conf = LEGACY_TIME_PARSER_POLICY_CONF) {
    df => df.withColumn("c1", to_date(col("c0"), "yyyy-MM-dd"))
  }

  testSparkResultsAreEqual("to_date yyyy/MM/dd LEGACY",
    datesAsStrings,
    conf = LEGACY_TIME_PARSER_POLICY_CONF) {
    df => df.withColumn("c1", to_date(col("c0"), "yyyy/MM/dd"))
  }

  testSparkResultsAreEqual("to_date dd/MM/yyyy",
      datesAsStrings,
      conf = CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", to_date(col("c0"), "dd/MM/yyyy"))
  }

  testSparkResultsAreEqual("to_date dd/MM/yyyy LEGACY",
    datesAsStrings,
    conf = LEGACY_TIME_PARSER_POLICY_CONF) {
    df => df.withColumn("c1", to_date(col("c0"), "dd/MM/yyyy"))
  }

  testSparkResultsAreEqual("to_date parse date",
      dates,
      conf = CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", to_date(col("c0"), "yyyy-MM-dd"))
  }

  testSparkResultsAreEqual("to_date parse timestamp",
      timestamps,
      conf = CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", to_date(col("c0"), "yyyy-MM-dd"))
  }

  testSparkResultsAreEqual("to_timestamp yyyy-MM-dd",
      timestampsAsStrings,
      conf = CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", to_timestamp(col("c0"), "yyyy-MM-dd"))
  }

  testSparkResultsAreEqual("to_timestamp dd/MM/yyyy",
      timestampsAsStrings,
      conf = CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", to_timestamp(col("c0"), "dd/MM/yyyy"))
  }

  testSparkResultsAreEqual("to_date default pattern",
      datesAsStrings,
      CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", to_date(col("c0")))
  }

  testSparkResultsAreEqual("unix_timestamp parse date",
      timestampsAsStrings,
      CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", unix_timestamp(col("c0"), "yyyy-MM-dd"))
  }

  testSparkResultsAreEqual("unix_timestamp parse yyyy/MM",
    timestampsAsStrings,
    CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", unix_timestamp(col("c0"), "yyyy/MM"))
  }

  testSparkResultsAreEqual("to_unix_timestamp parse yyyy/MM",
    timestampsAsStrings,
    CORRECTED_TIME_PARSER_POLICY) {
    df => {
      df.createOrReplaceTempView("df")
      df.sqlContext.sql("SELECT c0, to_unix_timestamp(c0, 'yyyy/MM') FROM df")
    }
  }

  testSparkResultsAreEqual("to_unix_timestamp parse yyyy/MM (improvedTimeOps)",
    timestampsAsStrings,
    new SparkConf().set(SQLConf.LEGACY_TIME_PARSER_POLICY.key, "CORRECTED")
        .set(RapidsConf.IMPROVED_TIMESTAMP_OPS.key, "true")) {
    df => {
      df.createOrReplaceTempView("df")
      df.sqlContext.sql("SELECT c0, to_unix_timestamp(c0, 'yyyy/MM') FROM df")
    }
  }

  testSparkResultsAreEqual("unix_timestamp parse timestamp",
      timestampsAsStrings,
      CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", unix_timestamp(col("c0"), "yyyy-MM-dd HH:mm:ss"))
  }

  testSparkResultsAreEqual("unix_timestamp parse yyyy-MM-dd HH:mm:ss LEGACY",
    timestampsAsStrings,
    LEGACY_TIME_PARSER_POLICY_CONF) {
    df => df.withColumn("c1", unix_timestamp(col("c0"), "yyyy-MM-dd HH:mm:ss"))
  }

  testSparkResultsAreEqual("unix_timestamp parse yyyy/MM/dd HH:mm:ss LEGACY",
    timestampsAsStrings,
    LEGACY_TIME_PARSER_POLICY_CONF) {
    df => df.withColumn("c1", unix_timestamp(col("c0"), "yyyy/MM/dd HH:mm:ss"))
  }

  testSparkResultsAreEqual("unix_timestamp parse timestamp millis (fall back to CPU)",
    timestampsAsStrings,
    new SparkConf().set(SQLConf.LEGACY_TIME_PARSER_POLICY.key, "CORRECTED")
      .set(RapidsConf.TEST_ALLOWED_NONGPU.key,
        "ProjectExec,Alias,UnixTimestamp,Literal,ShuffleExchangeExec")) {
    df => df.withColumn("c1", unix_timestamp(col("c0"), "yyyy-MM-dd HH:mm:ss.SSS"))
  }

  testSparkResultsAreEqual("unix_timestamp parse timestamp default pattern",
      timestampsAsStrings,
      CORRECTED_TIME_PARSER_POLICY) {
    df => df.withColumn("c1", unix_timestamp(col("c0")))
  }

  test("fall back to CPU when policy is LEGACY and unsupported format is used") {
    val e = intercept[IllegalArgumentException] {
      val df = withGpuSparkSession(spark => {
        val formatString = "u" // we do not support this legacy format on GPU
        timestampsAsStrings(spark)
            .repartition(2)
            .withColumn("c1", unix_timestamp(col("c0"), formatString))
      }, LEGACY_TIME_PARSER_POLICY_CONF)
      df.collect()
    }
    assert(e.getMessage.contains(
      "Part of the plan is not columnar class org.apache.spark.sql.execution.ProjectExec"))
  }

  test("unsupported format") {

    // capture plans
    val plans = new ListBuffer[SparkPlanMeta[SparkPlan]]()
    GpuOverrides.addListener(
        (plan: SparkPlanMeta[SparkPlan], _: SparkPlan, _: Seq[Optimization]) => {
      plans.append(plan)
    })

    val e = intercept[IllegalArgumentException] {
      val df = withGpuSparkSession(spark => {
        datesAsStrings(spark)
          .repartition(2)
          .withColumn("c1", to_date(col("c0"), "F"))
      }, CORRECTED_TIME_PARSER_POLICY)
      df.collect()
    }
    assert(e.getMessage.contains(
      "Part of the plan is not columnar class org.apache.spark.sql.execution.ProjectExec"))

    val planStr = plans.last.toString
    assert(planStr.contains("Failed to convert Unsupported character: F"))
    // make sure we aren't suggesting enabling INCOMPATIBLE_DATE_FORMATS for something we
    // can never support
    assert(!planStr.contains(RapidsConf.INCOMPATIBLE_DATE_FORMATS.key))
  }

  test("parse now") {
    def now(spark: SparkSession) = {
      import spark.implicits._
      Seq("now").toDF("c0")
          .repartition(2)
          .withColumn("c1", unix_timestamp(col("c0"), "yyyy-MM-dd HH:mm:ss"))
    }
    val startTimeSeconds = System.currentTimeMillis()/1000L
    val cpuNowSeconds = withCpuSparkSession(now).collect().head.toSeq(1).asInstanceOf[Long]
    val gpuNowSeconds = withGpuSparkSession(now).collect().head.toSeq(1).asInstanceOf[Long]
    assert(cpuNowSeconds >= startTimeSeconds)
    assert(gpuNowSeconds >= startTimeSeconds)
    // CPU ran first so cannot have a greater value than the GPU run (but could be the same second)
    assert(cpuNowSeconds <= gpuNowSeconds)
  }

  test("Regex: Remove whitespace from month and day") {
    testRegex(REMOVE_WHITESPACE_FROM_MONTH_DAY,
    Seq("1- 1-1", "1-1- 1", "1- 1- 1", null),
    Seq("1-1-1", "1-1-1", "1-1-1", null))
  }

  test("Regex: Fix single digit month") {
    testRegex(FIX_SINGLE_DIGIT_MONTH,
      Seq("1-2-3", "1111-2-3", "2000-7-7\n9\t8568:\n", null),
      Seq("1-02-3", "1111-02-3", "2000-07-7\n9\t8568:\n", null))
  }

  test("Regex: Fix single digit day followed by non digit char") {
    // single digit day followed by non digit
    testRegex(FIX_SINGLE_DIGIT_DAY,
      Seq("1111-02-3 ", "1111-02-3:", "2000-03-192", "2000-07-7\n9\t8568:\n", null),
      Seq("1111-02-03 ", "1111-02-03:", "2000-03-192", "2000-07-07\n9\t8568:\n", null))
  }

  test("Regex: Fix single digit day at end of string") {
    // single digit day at end of string
    testRegex(FIX_SINGLE_DIGIT_DAY,
      Seq("1-02-3", "1111-02-3", "1111-02-03", "2000-03-192", null),
      Seq("1-02-03", "1111-02-03", "1111-02-03", "2000-03-192", null))
  }

  test("Regex: Fix single digit hour 1") {
    // single digit hour with space separating date and time
    testRegex(FIX_SINGLE_DIGIT_HOUR,
      Seq("2001-12-31 1:2:3", "2001-12-31 1:22:33", null),
      Seq("2001-12-31 01:2:3", "2001-12-31 01:22:33", null))
  }

  test("Regex: Fix single digit hour 2") {
    // single digit hour with 'T' separating date and time
    // note that the T gets replaced with whitespace in this case
    testRegex(FIX_SINGLE_DIGIT_HOUR,
      Seq("2001-12-31T1:2:3", "2001-12-31T1:22:33", null),
      Seq("2001-12-31 01:2:3", "2001-12-31 01:22:33", null))
  }

  test("Regex: Fix single digit minute") {
    // single digit minute at end of string
    testRegex(FIX_SINGLE_DIGIT_MINUTE,
      Seq("2001-12-31 01:2:3", "2001-12-31 01:22:33", null),
      Seq("2001-12-31 01:02:3", "2001-12-31 01:22:33", null))
  }

  test("Regex: Fix single digit second followed by non digit") {
    // single digit second followed by non digit
    testRegex(FIX_SINGLE_DIGIT_SECOND,
      Seq("2001-12-31 01:02:3:", "2001-12-31 01:22:33:", "2001-12-31 01:02:3 ", null),
      Seq("2001-12-31 01:02:03:", "2001-12-31 01:22:33:", "2001-12-31 01:02:03 ", null))
  }

  test("Regex: Fix single digit second at end of string") {
    // single digit day at end of string
    testRegex(FIX_SINGLE_DIGIT_SECOND,
      Seq("2001-12-31 01:02:3", "2001-12-31 01:22:33", null),
      Seq("2001-12-31 01:02:03", "2001-12-31 01:22:33", null))
  }

  test("Regex: Apply all date rules") {
    // end to end test of all date rules being applied in sequence
    val testPairs = Seq(
      ("2001- 1-1", "2001-01-01"),
      ("2001-1- 1", "2001-01-01"),
      ("2001- 1- 1", "2001-01-01"),
      ("1999-12-31", "1999-12-31"),
      ("1999-2-31", "1999-02-31"),
      ("1999-2-3:", "1999-02-03:"),
      ("1999-2-3", "1999-02-03"),
      ("1999-2-3 1:2:3.4", "1999-02-03 1:2:3.4"),
      ("1999-2-3T1:2:3.4", "1999-02-03T1:2:3.4")
    )
    val values = testPairs.map(_._1)
    val expected = testPairs.map(_._2)
    withResource(ColumnVector.fromStrings(values: _*)) { v =>
      withResource(ColumnVector.fromStrings(expected: _*)) { expected =>
        val actual = FIX_DATES.foldLeft(v.incRefCount())((a, b) => {
          withResource(a) {
            _.stringReplaceWithBackrefs(b.search, b.replace)
          }
        })
        withResource(actual) { _ =>
          CudfTestHelper.assertColumnsAreEqual(expected, actual)
        }
      }
    }
  }

  test("Regex: Apply all date and timestamp rules") {
    // end to end test of all date and timestamp rules being applied in sequence
    val testPairs = Seq(
      ("2001- 1-1", "2001-01-01"),
      ("2001-1- 1", "2001-01-01"),
      ("2001- 1- 1", "2001-01-01"),
      ("1999-12-31", "1999-12-31"),
      ("1999-2-31", "1999-02-31"),
      ("1999-2-3:", "1999-02-03:"),
      ("1999-2-3", "1999-02-03"),
      ("1999-2-3 1:2:3.4", "1999-02-03 01:02:03.4"),
      ("1999-2-3T1:2:3.4", "1999-02-03 01:02:03.4")
    )
    val values = testPairs.map(_._1)
    val expected = testPairs.map(_._2)
    withResource(ColumnVector.fromStrings(values: _*)) { v =>
      withResource(ColumnVector.fromStrings(expected: _*)) { expected =>
        val actual = (FIX_DATES ++ FIX_TIMESTAMPS).foldLeft(v.incRefCount())((a, b) => {
          withResource(a) {
            _.stringReplaceWithBackrefs(b.search, b.replace)
          }
        })
        withResource(actual) { _ =>
          CudfTestHelper.assertColumnsAreEqual(expected, actual)
        }
      }
    }
  }

  private def testRegex(rule: RegexReplace, values: Seq[String], expected: Seq[String]): Unit = {
    withResource(ColumnVector.fromStrings(values: _*)) { v =>
      withResource(ColumnVector.fromStrings(expected: _*)) { expected =>
        withResource(v.stringReplaceWithBackrefs(rule.search, rule.replace)) { actual =>
          CudfTestHelper.assertColumnsAreEqual(expected, actual)
        }
      }
    }
  }

  // just show the failures so we don't have to manually parse all
  // the output to find which ones failed
  override def compareResults(
      sort: Boolean,
      maxFloatDiff: Double,
      fromCpu: Array[Row],
      fromGpu: Array[Row]): Unit = {
    assert(fromCpu.length === fromGpu.length)

    val failures = fromCpu.zip(fromGpu).zipWithIndex.filterNot {
      case ((cpu, gpu), _) => super.compare(cpu, gpu, 0.0001)
    }

    if (failures.nonEmpty) {
      val str = failures.map {
        case ((cpu, gpu), i) =>
          s"""
             |[#$i] CPU: $cpu
             |[#$i] GPU: $gpu
             |
             |""".stripMargin
      }.mkString("\n")
      fail(s"Mismatch between CPU and GPU for the following rows:\n$str")
    }
  }

  private def dates(spark: SparkSession) = {
    import spark.implicits._
    dateValues.toDF("c0")
  }

  private def timestamps(spark: SparkSession) = {
    import spark.implicits._
    tsValues.toDF("c0")
  }

  private def timestampsAsStrings(spark: SparkSession) = {
    import spark.implicits._
    timestampValues.toDF("c0")
  }

  private def datesAsStrings(spark: SparkSession) = {
    import spark.implicits._
    val values = Seq(
      DateUtils.EPOCH,
      DateUtils.NOW,
      DateUtils.TODAY,
      DateUtils.YESTERDAY,
      DateUtils.TOMORROW
    ) ++ timestampValues
    values.toDF("c0")
  }

  private val singleDigits = Seq("1999-1-1 ",
    "1999-1-1 11",
    "1999-1-1 11:",
    "1999-1-1 11:5",
    "1999-1-1 11:59",
    "1999-1-1 11:59:",
    "1999-1-1 11:59:5",
    "1999-1-1 11:59:59",
    "1999-1-1",
    "1999-1-1 ",
    "1999-1-1 1",
    "1999-1-1 1:",
    "1999-1-1 1:2",
    "1999-1-1 1:2:",
    "1999-1-1 1:2:3",
    "1999-1-1 1:2:3.",
    "1999-1-1 1:12:3.",
    "1999-1-1 11:12:3.",
    "1999-1-1 11:2:3.",
    "1999-1-1 11:2:13.",
    "1999-1-1 1:2:3.4")

  private val timestampValues = Seq(
    "",
    "null",
    null,
    "\n",
    "1999-12-31 11",
    "1999-12-31 11:",
    "1999-12-31 11:5",
    "1999-12-31 11:59",
    "1999-12-31 11:59:",
    "1999-12-31 11:59:5",
    "1999-12-31 11:59:59",
    "  1999-12-31 11:59:59",
    "\t1999-12-31 11:59:59",
    "\t1999-12-31 11:59:59\n",
    "1999-12-31 11:59:59.",
    "1999-12-31 11:59:59.9",
    " 1999-12-31 11:59:59.9",
    "1999-12-31 11:59:59.99",
    "1999-12-31 11:59:59.999",
    "1999-12-31 11:59:59.9999",
    "1999-12-31 11:59:59.99999",
    "1999-12-31 11:59:59.999999",
    "1999-12-31 11:59:59.9999999",
    "1999-12-31 11:59:59.99999999",
    "1999-12-31 11:59:59.999999999",
    "31/12/1999",
    "31/12/1999 11:59:59.999",
    "1999-12-3",
    "1999-12-31",
    "1999/12/31",
    "1999-12",
    "1999/12",
    "1975/06",
    "1975/06/18",
    "1975/06/18 06:48:57",
    "1999-12-29\n",
    "\t1999-12-30",
    " \n1999-12-31",
    "1999/12/31",
    "2001- 1-1",
    "2001-1- 1",
    "2001- 1- 1",
    "2000-3-192",
    // interesting test cases from fuzzing that original triggered differences between CPU and GPU
    "2000-1- 099\n305\n 7-390--.0:-",
    "2000-7-7\n9\t8568:\n",
    "2000-\t5-2.7.44584.9935") ++ singleDigits ++ singleDigits.map(_.replace('-', '/'))

  private val dateValues = Seq(
    Date.valueOf("2020-07-24"),
    Date.valueOf("2020-07-25"),
    Date.valueOf("1999-12-31"))

  private val tsValues = Seq(
    Timestamp.valueOf("2015-07-24 10:00:00.3"),
    Timestamp.valueOf("2015-07-25 02:02:02.2"),
    Timestamp.valueOf("1999-12-31 11:59:59.999")
  )

}
