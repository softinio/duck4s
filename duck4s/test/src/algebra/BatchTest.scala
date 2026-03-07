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

package com.softinio.duck4s.algebra

import com.softinio.duck4s.{DuckDBConnection, *}
import com.softinio.duck4s.algebra.{
  BatchBinder,
  DuckDBConfig,
  DuckDBError,
  ParameterBinder
}
import com.softinio.duck4s.algebra.BatchBinder.given
import com.softinio.duck4s.algebra.ParameterBinder.given

class BatchTest extends munit.FunSuite:

  test("batch insert - simple batch"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE batch_test (id INTEGER, name VARCHAR)"
        )

        batchResult <- conn.withBatch("INSERT INTO batch_test VALUES (?, ?)"):
          batch =>
            for
              // Add multiple rows to batch
              _ <- batch.addBatch((1, "Alice"))
              _ <- batch.addBatch((2, "Bob"))
              _ <- batch.addBatch((3, "Charlie"))
              result <- batch.executeBatch()
            yield result

        rs <- conn.executeQuery("SELECT COUNT(*) as cnt FROM batch_test")
        count =
          assert(rs.next())
          val c = rs.getInt("cnt")
          rs.close()
          c

        // Verify all data
        rs2 <- conn.executeQuery("SELECT * FROM batch_test ORDER BY id")
      yield
        assertEquals(batchResult.successCount, 3)
        assertEquals(batchResult.failureCount, 0)
        assertEquals(count, 3)

        assert(rs2.next())
        assertEquals(rs2.getInt("id"), 1)
        assertEquals(rs2.getString("name"), "Alice")

        assert(rs2.next())
        assertEquals(rs2.getInt("id"), 2)
        assertEquals(rs2.getString("name"), "Bob")

        assert(rs2.next())
        assertEquals(rs2.getInt("id"), 3)
        assertEquals(rs2.getString("name"), "Charlie")

        assert(!rs2.next())
        rs2.close()

    assert(result.isRight)

  test("batch insert - with 3-tuple"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE batch_3tuple (id INTEGER, name VARCHAR, age INTEGER)"
        )

        batchResult <- conn.withBatch(
          "INSERT INTO batch_3tuple VALUES (?, ?, ?)"
        ): batch =>
          for
            _ <- batch.addBatch((1, "Alice", 25))
            _ <- batch.addBatch((2, "Bob", 30))
            result <- batch.executeBatch()
          yield result

        rs <- conn.executeQuery("SELECT * FROM batch_3tuple ORDER BY id")
      yield
        assertEquals(batchResult.successCount, 2)

        assert(rs.next())
        assertEquals(rs.getInt("id"), 1)
        assertEquals(rs.getString("name"), "Alice")
        assertEquals(rs.getInt("age"), 25)

        assert(rs.next())
        assertEquals(rs.getInt("id"), 2)
        assertEquals(rs.getString("name"), "Bob")
        assertEquals(rs.getInt("age"), 30)

        rs.close()

    assert(result.isRight)

  test("batch insert - with 4-tuple"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          """CREATE TABLE batch_4tuple (
            |  id INTEGER,
            |  name VARCHAR,
            |  price DOUBLE,
            |  active BOOLEAN
            |)""".stripMargin
        )

        batchResult <- conn.withBatch(
          "INSERT INTO batch_4tuple VALUES (?, ?, ?, ?)"
        ): batch =>
          for
            _ <- batch.addBatch((1, "Product A", 19.99, true))
            _ <- batch.addBatch((2, "Product B", 29.99, false))
            result <- batch.executeBatch()
          yield result

        rs <- conn.executeQuery("SELECT * FROM batch_4tuple ORDER BY id")
      yield
        assertEquals(batchResult.successCount, 2)

        assert(rs.next())
        assertEquals(rs.getInt("id"), 1)
        assertEquals(rs.getString("name"), "Product A")
        assertEquals(rs.getDouble("price"), 19.99)
        assertEquals(rs.getBoolean("active"), true)

        assert(rs.next())
        assertEquals(rs.getInt("id"), 2)
        assertEquals(rs.getString("name"), "Product B")
        assertEquals(rs.getDouble("price"), 29.99)
        assertEquals(rs.getBoolean("active"), false)

        rs.close()

    assert(result.isRight)

  test("batch insert - large batch"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE batch_large (id INTEGER, value INTEGER)"
        )

        batchResult <- conn.withBatch("INSERT INTO batch_large VALUES (?, ?)"):
          batch =>
            val addResults = (1 to 1000).map { i =>
              batch.addBatch((i, i * 10))
            }

            // Check all adds succeeded
            assert(addResults.forall(_.isRight))

            batch.executeBatch()

        rs <- conn.executeQuery(
          "SELECT COUNT(*) as cnt, SUM(value) as total FROM batch_large"
        )
      yield
        assertEquals(batchResult.successCount, 1000)
        assertEquals(batchResult.failureCount, 0)

        assert(rs.next())
        assertEquals(rs.getInt("cnt"), 1000)
        assertEquals(rs.getLong("total"), (1 to 1000).map(_ * 10).sum.toLong)
        rs.close()

    assert(result.isRight)

  test("batch insert - clear batch"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE batch_clear (id INTEGER, name VARCHAR)"
        )

        batchResult <- conn.withBatch("INSERT INTO batch_clear VALUES (?, ?)"):
          batch =>
            for
              // Add some entries
              _ <- batch.addBatch((1, "Alice"))
              _ <- batch.addBatch((2, "Bob"))

              // Clear the batch
              _ <- batch.clearBatch()

              // Add new entries
              _ <- batch.addBatch((3, "Charlie"))
              _ <- batch.addBatch((4, "David"))

              result <- batch.executeBatch()
            yield result

        rs <- conn.executeQuery("SELECT * FROM batch_clear ORDER BY id")
      yield
        assertEquals(batchResult.successCount, 2) // Only Charlie and David

        assert(rs.next())
        assertEquals(rs.getInt("id"), 3)
        assertEquals(rs.getString("name"), "Charlie")

        assert(rs.next())
        assertEquals(rs.getInt("id"), 4)
        assertEquals(rs.getString("name"), "David")

        assert(!rs.next())
        rs.close()

    assert(result.isRight)

  test("batch insert - with Option values"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE batch_option (id INTEGER, name VARCHAR, email VARCHAR)"
        )

        batchResult <- conn.withBatch(
          "INSERT INTO batch_option VALUES (?, ?, ?)"
        ): batch =>
          for
            _ <- batch.addBatch((1, "Alice", Option("alice@example.com")))
            _ <- batch.addBatch((2, "Bob", Option.empty[String]))
            result <- batch.executeBatch()
          yield result

        rs <- conn.executeQuery("SELECT * FROM batch_option ORDER BY id")
      yield
        assertEquals(batchResult.successCount, 2)

        assert(rs.next())
        assertEquals(rs.getInt("id"), 1)
        assertEquals(rs.getString("name"), "Alice")
        assertEquals(rs.getString("email"), "alice@example.com")

        assert(rs.next())
        assertEquals(rs.getInt("id"), 2)
        assertEquals(rs.getString("name"), "Bob")
        rs.getString("email")
        assert(rs.wasNull())

        rs.close()

    assert(result.isRight)

  test("batch insert - float, date, BigDecimal, bytes"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          """CREATE TABLE batch_types2 (
            |  id INTEGER,
            |  f FLOAT,
            |  d DATE,
            |  dec DECIMAL(10,2),
            |  b BLOB
            |)""".stripMargin
        )
        date  = java.sql.Date.valueOf("2024-06-15")
        price = BigDecimal("99.99")
        bytes = "duck4s".getBytes("UTF-8")

        batchResult <- conn.withBatch(
          "INSERT INTO batch_types2 VALUES (?, ?, ?, ?, ?)"
        ): batch =>
          for
            _ <- batch.addBatch((1, 1.5f, date, price, bytes))
            result <- batch.executeBatch()
          yield result

        rs <- conn.executeQuery("SELECT * FROM batch_types2 WHERE id = 1")
      yield
        assertEquals(batchResult.successCount, 1)
        assert(rs.next())
        assertEquals(rs.getFloat("f"), 1.5f)
        assertEquals(rs.getDate("d"), date)
        assertEquals(BigDecimal(rs.getBigDecimal("dec")), price)
        assertEquals(rs.getBytes("b").toList, bytes.toList)
        rs.close()

    assert(result.isRight)

  test("batch insert - java.time binders"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          """CREATE TABLE batch_jtime (
            |  id INTEGER,
            |  ld DATE,
            |  ldt TIMESTAMP,
            |  odt TIMESTAMPTZ
            |)""".stripMargin
        )
        localDate     = java.time.LocalDate.of(2024, 3, 1)
        localDateTime = java.time.LocalDateTime.of(2024, 3, 1, 9, 0, 0)
        offsetDateTime = java.time.OffsetDateTime.of(
          localDateTime,
          java.time.ZoneOffset.UTC
        )

        batchResult <- conn.withBatch(
          "INSERT INTO batch_jtime VALUES (?, ?, ?, ?)"
        ): batch =>
          for
            _ <- batch.addBatch((1, localDate, localDateTime, offsetDateTime))
            result <- batch.executeBatch()
          yield result

        rs <- conn.executeQuery("SELECT * FROM batch_jtime WHERE id = 1")
      yield
        assertEquals(batchResult.successCount, 1)
        assert(rs.next())
        assertEquals(
          rs.getObject("ld", classOf[java.time.LocalDate]),
          localDate
        )
        assertEquals(
          rs.getObject("ldt", classOf[java.time.LocalDateTime]),
          localDateTime
        )
        rs.close()

    assert(result.isRight)

  test("batch insert - with Timestamp and UUID"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE batch_ts_uuid (id INTEGER, ts TIMESTAMP, uid UUID)"
        )
        ts1 = java.sql.Timestamp.valueOf("2024-01-01 00:00:00")
        ts2 = java.sql.Timestamp.valueOf("2024-06-15 12:30:00")
        uuid1 = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        uuid2 = java.util.UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

        batchResult <- conn.withBatch(
          "INSERT INTO batch_ts_uuid VALUES (?, ?, ?)"
        ): batch =>
          for
            _ <- batch.addBatch((1, ts1, uuid1))
            _ <- batch.addBatch((2, ts2, uuid2))
            result <- batch.executeBatch()
          yield result

        rs <- conn.executeQuery(
          "SELECT * FROM batch_ts_uuid ORDER BY id"
        )
      yield
        assertEquals(batchResult.successCount, 2)

        assert(rs.next())
        assertEquals(rs.getInt("id"), 1)
        assertEquals(rs.getTimestamp("ts"), ts1)
        assertEquals(rs.getObject("uid", classOf[java.util.UUID]), uuid1)

        assert(rs.next())
        assertEquals(rs.getInt("id"), 2)
        assertEquals(rs.getTimestamp("ts"), ts2)
        assertEquals(rs.getObject("uid", classOf[java.util.UUID]), uuid2)

        assert(!rs.next())
        rs.close()

    assert(result.isRight)

  test("batch insert - transaction with batch"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE batch_tx (id INTEGER, value INTEGER)"
        )

        txResult <- conn.withTransaction: txConn =>
          for
            batchResult <- txConn.withBatch(
              "INSERT INTO batch_tx VALUES (?, ?)"
            ): batch =>
              for
                _ <- batch.addBatch((1, 100))
                _ <- batch.addBatch((2, 200))
                _ <- batch.addBatch((3, 300))
                result <- batch.executeBatch()
              yield result

            // Verify within transaction
            rs <- txConn.executeQuery(
              "SELECT SUM(value) as total FROM batch_tx"
            )
            total =
              assert(rs.next())
              val t = rs.getInt("total")
              rs.close()
              t
          yield (batchResult, total)

        // Verify after transaction
        rs <- conn.executeQuery("SELECT COUNT(*) as cnt FROM batch_tx")
      yield
        val (batchResult, total) = txResult
        assertEquals(batchResult.successCount, 3)
        assertEquals(total, 600)

        assert(rs.next())
        assertEquals(rs.getInt("cnt"), 3)
        rs.close()

    assert(result.isRight)
