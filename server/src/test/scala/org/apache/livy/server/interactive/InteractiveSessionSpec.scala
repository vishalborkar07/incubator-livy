/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.livy.server.interactive

import java.net.URI

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import org.apache.spark.launcher.SparkLauncher
import org.json4s.{DefaultFormats, Extraction, JValue}
import org.json4s.jackson.JsonMethods.parse
import org.mockito.{Matchers => MockitoMatchers}
import org.mockito.Matchers._
import org.mockito.Mockito.{atLeastOnce, verify, when}
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import org.scalatest.concurrent.Eventually._
import org.scalatestplus.mockito.MockitoSugar.mock

import org.apache.livy.{ExecuteRequest, JobHandle, LivyBaseUnitTestSuite, LivyConf}
import org.apache.livy.rsc.{PingJob, RSCClient, RSCConf}
import org.apache.livy.rsc.driver.StatementState
import org.apache.livy.server.AccessManager
import org.apache.livy.server.recovery.SessionStore
import org.apache.livy.sessions.{PySpark, SessionState, Spark}
import org.apache.livy.utils.{AppInfo, SparkApp}

class InteractiveSessionSpec extends FunSpec
    with Matchers with BeforeAndAfterAll with LivyBaseUnitTestSuite {

  private val livyConf = new LivyConf()
  livyConf.set(LivyConf.REPL_JARS, "dummy.jar")
    .set(LivyConf.LIVY_SPARK_VERSION, sys.env("LIVY_SPARK_VERSION"))
    .set(LivyConf.LIVY_SPARK_SCALA_VERSION, sys.env("LIVY_SCALA_VERSION"))

  implicit val formats = DefaultFormats

  private var session: InteractiveSession = null
  private val accessManager = new AccessManager(livyConf)

  private def createSession(
      sessionStore: SessionStore = mock[SessionStore],
      mockApp: Option[SparkApp] = None): InteractiveSession = {
    assume(sys.env.get("SPARK_HOME").isDefined, "SPARK_HOME is not set.")

    val req = new CreateInteractiveRequest()
    req.kind = PySpark
    req.driverMemory = Some("512m")
    req.driverCores = Some(1)
    req.executorMemory = Some("512m")
    req.executorCores = Some(1)
    req.name = Some("InteractiveSessionSpec")
    req.conf = Map(
      SparkLauncher.DRIVER_EXTRA_CLASSPATH -> sys.props("java.class.path"),
      RSCConf.Entry.LIVY_JARS.key() -> ""
    )
    InteractiveSession.create(0, None, null, None, livyConf, accessManager, req,
      sessionStore, None, mockApp)
  }

  private def executeStatement(code: String, codeType: Option[String] = None): JValue = {
    val id = session.executeStatement(ExecuteRequest(code, codeType)).id
    eventually(timeout(30 seconds), interval(100 millis)) {
      val s = session.getStatement(id).get
      s.state.get() shouldBe StatementState.Available
      parse(s.output)
    }
  }

  override def afterAll(): Unit = {
    if (session != null) {
      Await.ready(session.stop(), 30 seconds)
      session = null
    }
    super.afterAll()
  }

  private def withSession(desc: String)(fn: (InteractiveSession) => Unit): Unit = {
    it(desc) {
      assume(session != null, "No active session.")
      eventually(timeout(60 seconds), interval(100 millis)) {
        session.state shouldBe (SessionState.Idle)
      }
      fn(session)
    }
  }

  describe("A spark session") {

    it("should get scala version matched jars with livy.repl.jars") {
      val testedJars = Seq(
        "test_2.10-0.1.jar",
        "local://dummy-path/test/test1_2.10-1.0.jar",
        "file:///dummy-path/test/test2_2.11-1.0-SNAPSHOT.jar",
        "hdfs:///dummy-path/test/test3.jar",
        "non-jar",
        "dummy.jar"
      )
      val livyConf = new LivyConf(false)
        .set(LivyConf.REPL_JARS, testedJars.mkString(","))
        .set(LivyConf.LIVY_SPARK_VERSION, sys.env("LIVY_SPARK_VERSION"))
        .set(LivyConf.LIVY_SPARK_SCALA_VERSION, "2.10")
      val properties = InteractiveSession.prepareBuilderProp(Map.empty, Spark, livyConf)
      assert(properties(LivyConf.SPARK_JARS).split(",").toSet === Set("test_2.10-0.1.jar",
        "local://dummy-path/test/test1_2.10-1.0.jar",
        "hdfs:///dummy-path/test/test3.jar",
        "dummy.jar"))

      livyConf.set(LivyConf.LIVY_SPARK_SCALA_VERSION, "2.11")
      val properties1 = InteractiveSession.prepareBuilderProp(Map.empty, Spark, livyConf)
      assert(properties1(LivyConf.SPARK_JARS).split(",").toSet === Set(
        "file:///dummy-path/test/test2_2.11-1.0-SNAPSHOT.jar",
        "hdfs:///dummy-path/test/test3.jar",
        "dummy.jar"))
    }

    it("should set rsc jars through livy conf") {
      val rscJars = Set(
        "dummy.jar",
        "local:///dummy-path/dummy1.jar",
        "file:///dummy-path/dummy2.jar",
        "hdfs:///dummy-path/dummy3.jar")
      val livyConf = new LivyConf(false)
        .set(LivyConf.REPL_JARS, "dummy.jar")
        .set(LivyConf.RSC_JARS, rscJars.mkString(","))
        .set(LivyConf.LIVY_SPARK_VERSION, sys.env("LIVY_SPARK_VERSION"))
        .set(LivyConf.LIVY_SPARK_SCALA_VERSION, "2.10")
      val properties = InteractiveSession.prepareBuilderProp(Map.empty, Spark, livyConf)
      // if livy.rsc.jars is configured in LivyConf, it should be passed to RSCConf.
      properties(RSCConf.Entry.LIVY_JARS.key()).split(",").toSet === rscJars

      val rscJars1 = Set(
        "foo.jar",
        "local:///dummy-path/foo1.jar",
        "file:///dummy-path/foo2.jar",
        "hdfs:///dummy-path/foo3.jar")
      val properties1 = InteractiveSession.prepareBuilderProp(
        Map(RSCConf.Entry.LIVY_JARS.key() -> rscJars1.mkString(",")), Spark, livyConf)
      // if rsc jars are configured both in LivyConf and RSCConf, RSCConf should take precedence.
      properties1(RSCConf.Entry.LIVY_JARS.key()).split(",").toSet === rscJars1
    }

    it("should update appId and appInfo and session store") {
      val mockApp = mock[SparkApp]
      val sessionStore = mock[SessionStore]
      session = createSession(sessionStore, Some(mockApp))
      session.start()

      val expectedAppId = "APPID"
      session.appIdKnown(expectedAppId)
      session.appId shouldEqual Some(expectedAppId)

      val expectedAppInfo = AppInfo(Some("DRIVER LOG URL"), Some("SPARK UI URL"))
      session.infoChanged(expectedAppInfo)
      session.appInfo shouldEqual expectedAppInfo

      verify(sessionStore, atLeastOnce()).save(
        MockitoMatchers.eq(InteractiveSession.RECOVERY_SESSION_TYPE), anyObject())

      session.state should (be(SessionState.Starting) or be(SessionState.Idle))
    }

    it("should propagate RSC configuration properties") {
      val livyConf = new LivyConf(false)
        .set(LivyConf.REPL_JARS, "dummy.jar")
        .set(RSCConf.Entry.SASL_QOP.key(), "foo")
        .set(RSCConf.Entry.RPC_CHANNEL_LOG_LEVEL.key(), "TRACE")
        .set(LivyConf.LIVY_SPARK_VERSION, sys.env("LIVY_SPARK_VERSION"))
        .set(LivyConf.LIVY_SPARK_SCALA_VERSION, "2.10")

      val properties = InteractiveSession.prepareBuilderProp(Map.empty, Spark, livyConf)
      assert(properties(RSCConf.Entry.SASL_QOP.key()) === "foo")
      assert(properties(RSCConf.Entry.RPC_CHANNEL_LOG_LEVEL.key()) === "TRACE")
    }

    withSession("should execute `1 + 2` == 3") { session =>
      val pyResult = executeStatement("1 + 2", Some("pyspark"))
      pyResult should equal (Extraction.decompose(Map(
        "status" -> "ok",
        "execution_count" -> 0,
        "data" -> Map("text/plain" -> "3")))
      )

      val scalaResult = executeStatement("1 + 2", Some("spark"))
      scalaResult should equal (Extraction.decompose(Map(
        "status" -> "ok",
        "execution_count" -> 1,
        "data" -> Map("text/plain" -> "res0: Int = 3\n")))
      )

      val rResult = executeStatement("1 + 2", Some("sparkr"))
      rResult should equal (Extraction.decompose(Map(
        "status" -> "ok",
        "execution_count" -> 2,
        "data" -> Map("text/plain" -> "[1] 3")))
      )
    }

    withSession("should report an error if accessing an unknown variable") { session =>
      val result = executeStatement("x")
      val expectedResult = Extraction.decompose(Map(
        "status" -> "error",
        "execution_count" -> 3,
        "ename" -> "NameError",
        "evalue" -> "name 'x' is not defined",
        "traceback" -> List(
          "Traceback (most recent call last):\n",
          "NameError: name 'x' is not defined\n"
        )
      ))

      result should equal (expectedResult)
      eventually(timeout(10 seconds), interval(30 millis)) {
        session.state shouldBe (SessionState.Idle)
      }
    }

    withSession("should get statement progress along with statement result") { session =>
      val code =
        """
          |from time import sleep
          |sleep(3)
        """.stripMargin
      val statement = session.executeStatement(ExecuteRequest(code, None))
      statement.progress should be (0.0)

      eventually(timeout(10 seconds), interval(100 millis)) {
        val s = session.getStatement(statement.id).get
        s.state.get() shouldBe StatementState.Available
        s.progress should be (1.0)
      }
    }

    withSession("should refresh last activity time when statement finished") { session =>
      val code =
        """
          |from time import sleep
          |sleep(3)
        """.stripMargin
      session.executeStatement(ExecuteRequest(code, None))
      val executionBeginTime = session.lastActivity

      eventually(timeout(10 seconds), interval(100 millis)) {
        session.state should be(SessionState.Idle)
        session.lastActivity should be > executionBeginTime
      }
    }

    withSession("should error out the session if the interpreter dies") { session =>
      session.executeStatement(ExecuteRequest("import os; os._exit(666)", None))
      eventually(timeout(30 seconds), interval(100 millis)) {
        session.state shouldBe a[SessionState.Error]
      }
    }
  }

  describe("recovery") {
    it("should recover named sessions") {
      val conf = new LivyConf()
      val sessionStore = mock[SessionStore]
      val mockClient = mock[RSCClient]
      when(mockClient.submit(any(classOf[PingJob]))).thenReturn(mock[JobHandle[Void]])
      val m = InteractiveRecoveryMetadata(
          78, Some("Test session"), None, "appTag", Spark, 0, 0,  null, None, None,
          Some(URI.create("")))
      val s = InteractiveSession.recover(m, conf, sessionStore, None, Some(mockClient))
      s.start()

      s.state shouldBe (SessionState.Recovering)

      s.appIdKnown("appId")
      verify(sessionStore, atLeastOnce()).save(
        MockitoMatchers.eq(InteractiveSession.RECOVERY_SESSION_TYPE), anyObject())
    }

    it("should recover sessions with no name") {
      val conf = new LivyConf()
      val sessionStore = mock[SessionStore]
      val mockClient = mock[RSCClient]
      when(mockClient.submit(any(classOf[PingJob]))).thenReturn(mock[JobHandle[Void]])
      val m = InteractiveRecoveryMetadata(
          78, None, None, "appTag", Spark, 0, 0, null, None, None, Some(URI.create("")))
      val s = InteractiveSession.recover(m, conf, sessionStore, None, Some(mockClient))
      s.start()

      s.state shouldBe (SessionState.Recovering)

      s.appIdKnown("appId")
      verify(sessionStore, atLeastOnce()).save(
        MockitoMatchers.eq(InteractiveSession.RECOVERY_SESSION_TYPE), anyObject())
    }

    it("should recover session to dead state if rscDriverUri is unknown") {
      val conf = new LivyConf()
      val sessionStore = mock[SessionStore]
      val m = InteractiveRecoveryMetadata(
        78, None, Some("appId"), "appTag", Spark, 0, 0, null, None, None, None)
      val s = InteractiveSession.recover(m, conf, sessionStore, None)
      s.start()
      s.state shouldBe a[SessionState.Dead]
      s.logLines().mkString should include("RSCDriver URI is unknown")
    }
  }
}
