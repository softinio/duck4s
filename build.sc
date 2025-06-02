/*
 * Copyright 2025 Salar Rahmanian
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

import mill._
import mill.scalalib._

val duckdbVersion = "1.1.3"
val munitVersion = "1.1.1"
val scalaVersions = Seq("3.3.6", "3.7.0")

object duck4s extends Cross[Duck4sModule](scalaVersions)

trait Duck4sModule extends ScalaModule with CrossScalaModule {

  def ivyDeps = Agg(
    ivy"org.duckdb:duckdb_jdbc:$duckdbVersion"
  )

  // Scaladoc configuration for documentation website generation
  def scalacOptions = {
    val common = Seq("-explain", "-explain-types")
    if (crossScalaVersion.startsWith("3.7")) {
      common :+ "-Xkind-projector:underscores"
    } else {
      common
    }
  }

  def scalaDocOptions = super.scalaDocOptions() ++ Seq(
    "-project", "Duck4s",
    "-project-version", "0.1.0",
    "-social-links:github::https://github.com/softinio/duck4s",
    "-groups",
    "-snippet-compiler:compile",
    "-external-mappings:" +
      ".*scala.*::scaladoc3::https://scala-lang.org/api/3.x/," +
      ".*java.*::javadoc::https://docs.oracle.com/javase/8/docs/api/"
  )

  object test extends ScalaTests with TestModule.Munit {
    def ivyDeps = Agg(
      ivy"org.scalameta::munit::$munitVersion"
    )
  }
}
