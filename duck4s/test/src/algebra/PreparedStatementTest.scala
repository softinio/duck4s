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
import com.softinio.duck4s.algebra.{DuckDBConfig, DuckDBError}

class PreparedStatementTest extends munit.FunSuite:

  test("prepared statement - simple query with parameters"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE ps_test (id INTEGER, name VARCHAR)"
        )
        _ <- conn.executeUpdate(
          "INSERT INTO ps_test VALUES (1, 'Alice'), (2, 'Bob')"
        )

        queryResult <- conn.withPreparedStatement(
          "SELECT * FROM ps_test WHERE id = ?"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 2)
            rs <- stmt.executeQuery()
          yield
            assert(rs.next())
            assertEquals(rs.getInt("id"), 2)
            assertEquals(rs.getString("name"), "Bob")
            assert(!rs.next())
            rs.close()
      yield queryResult

    assert(result.isRight)

  test("prepared statement - update with parameters"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE ps_update (id INTEGER, value INTEGER)"
        )
        _ <- conn.executeUpdate(
          "INSERT INTO ps_update VALUES (1, 100), (2, 200)"
        )

        updateResult <- conn.withPreparedStatement(
          "UPDATE ps_update SET value = ? WHERE id = ?"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 300)
            _ <- stmt.setInt(2, 1)
            count <- stmt.executeUpdate()
          yield count

        rs <- conn.executeQuery("SELECT value FROM ps_update WHERE id = 1")
      yield
        assertEquals(updateResult, 1)
        assert(rs.next())
        assertEquals(rs.getInt("value"), 300)
        rs.close()

    assert(result.isRight)

  test("prepared statement - multiple parameter types"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          """CREATE TABLE ps_types (
            |  id INTEGER,
            |  name VARCHAR,
            |  price DOUBLE,
            |  active BOOLEAN,
            |  quantity BIGINT
            |)""".stripMargin
        )

        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_types VALUES (?, ?, ?, ?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setString(2, "Product A")
            _ <- stmt.setDouble(3, 19.99)
            _ <- stmt.setBoolean(4, true)
            _ <- stmt.setLong(5, 1000L)
            count <- stmt.executeUpdate()
          yield count

        rs <- conn.executeQuery("SELECT * FROM ps_types WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
        assert(rs.next())
        assertEquals(rs.getInt("id"), 1)
        assertEquals(rs.getString("name"), "Product A")
        assertEquals(rs.getDouble("price"), 19.99)
        assertEquals(rs.getBoolean("active"), true)
        assertEquals(rs.getLong("quantity"), 1000L)
        rs.close()

    assert(result.isRight)

  test("prepared statement - null handling"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE ps_null (id INTEGER, name VARCHAR)"
        )

        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_null VALUES (?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setNull(2, java.sql.Types.VARCHAR)
            count <- stmt.executeUpdate()
          yield count

        rs <- conn.executeQuery("SELECT * FROM ps_null WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
        assert(rs.next())
        assertEquals(rs.getInt("id"), 1)
        rs.getString("name")
        assert(rs.wasNull())
        rs.close()

    assert(result.isRight)

  test("prepared statement - reuse for multiple executions"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE ps_reuse (id INTEGER, value INTEGER)"
        )

        results <- conn.withPreparedStatement(
          "INSERT INTO ps_reuse VALUES (?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setInt(2, 100)
            count1 <- stmt.executeUpdate()

            _ <- stmt.clearParameters()
            _ <- stmt.setInt(1, 2)
            _ <- stmt.setInt(2, 200)
            count2 <- stmt.executeUpdate()

            _ <- stmt.clearParameters()
            _ <- stmt.setInt(1, 3)
            _ <- stmt.setInt(2, 300)
            count3 <- stmt.executeUpdate()
          yield (count1, count2, count3)

        rs <- conn.executeQuery("SELECT COUNT(*) as cnt FROM ps_reuse")
      yield
        assertEquals(results, (1, 1, 1))
        assert(rs.next())
        assertEquals(rs.getInt("cnt"), 3)
        rs.close()

    assert(result.isRight)

  test("prepared statement - error on invalid parameter index"):
    val result = DuckDBConnection.withConnection(): conn =>
      conn.withPreparedStatement(
        "SELECT * FROM (VALUES (1, 2)) WHERE column0 = ?"
      ): stmt =>
        stmt.setInt(2, 1) // Invalid index - only one parameter

    assert(result.isLeft)
    result match
      case Left(DuckDBError.QueryError(message, _, _)) =>
        // Just verify we got a QueryError - the exact message may vary by JDBC driver version
        assert(message.nonEmpty)
      case _ => fail("Expected QueryError")

  test("prepared statement - timestamp parameter"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE ps_timestamp (id INTEGER, created_at TIMESTAMP)"
        )
        ts = java.sql.Timestamp.valueOf("2024-06-15 10:30:00")

        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_timestamp VALUES (?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setTimestamp(2, ts)
            count <- stmt.executeUpdate()
          yield count

        rs <- conn.executeQuery("SELECT * FROM ps_timestamp WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
        assert(rs.next())
        assertEquals(rs.getInt("id"), 1)
        assertEquals(rs.getTimestamp("created_at"), ts)
        rs.close()

    assert(result.isRight)

  test("prepared statement - uuid parameter"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE ps_uuid (id INTEGER, uid UUID)"
        )
        uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_uuid VALUES (?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setObject(2, uuid)
            count <- stmt.executeUpdate()
          yield count

        rs <- conn.executeQuery("SELECT * FROM ps_uuid WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
        assert(rs.next())
        assertEquals(rs.getInt("id"), 1)
        assertEquals(rs.getObject("uid", classOf[java.util.UUID]), uuid)
        rs.close()

    assert(result.isRight)

  test("prepared statement - float parameter"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate("CREATE TABLE ps_float (id INTEGER, val FLOAT)")
        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_float VALUES (?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setFloat(2, 3.14f)
            count <- stmt.executeUpdate()
          yield count
        rs <- conn.executeQuery("SELECT * FROM ps_float WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
        assert(rs.next())
        assertEquals(rs.getFloat("val"), 3.14f)
        rs.close()

    assert(result.isRight)

  test("prepared statement - date parameter"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate("CREATE TABLE ps_date (id INTEGER, d DATE)")
        date = java.sql.Date.valueOf("2024-06-15")
        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_date VALUES (?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setDate(2, date)
            count <- stmt.executeUpdate()
          yield count
        rs <- conn.executeQuery("SELECT * FROM ps_date WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
        assert(rs.next())
        assertEquals(rs.getDate("d"), date)
        rs.close()

    assert(result.isRight)

  test("prepared statement - BigDecimal parameter"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE ps_decimal (id INTEGER, price DECIMAL(10,2))"
        )
        price = BigDecimal("123.45")
        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_decimal VALUES (?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setBigDecimal(2, price)
            count <- stmt.executeUpdate()
          yield count
        rs <- conn.executeQuery("SELECT * FROM ps_decimal WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
        assert(rs.next())
        assertEquals(BigDecimal(rs.getBigDecimal("price")), price)
        rs.close()

    assert(result.isRight)

  test("prepared statement - bytes parameter (BLOB)"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate("CREATE TABLE ps_blob (id INTEGER, data BLOB)")
        bytes = "hello duck4s".getBytes("UTF-8")
        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_blob VALUES (?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setBytes(2, bytes)
            count <- stmt.executeUpdate()
          yield count
        rs <- conn.executeQuery("SELECT * FROM ps_blob WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
        assert(rs.next())
        assertEquals(rs.getBytes("data").toList, bytes.toList)
        rs.close()

    assert(result.isRight)

  test("prepared statement - java.time types"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          """CREATE TABLE ps_jtime (
            |  id INTEGER,
            |  ld DATE,
            |  ldt TIMESTAMP,
            |  odt TIMESTAMPTZ
            |)""".stripMargin
        )
        localDate     = java.time.LocalDate.of(2024, 6, 15)
        localDateTime = java.time.LocalDateTime.of(2024, 6, 15, 10, 30, 0)
        offsetDateTime = java.time.OffsetDateTime.of(
          localDateTime,
          java.time.ZoneOffset.UTC
        )
        insertResult <- conn.withPreparedStatement(
          "INSERT INTO ps_jtime VALUES (?, ?, ?, ?)"
        ): stmt =>
          for
            _ <- stmt.setInt(1, 1)
            _ <- stmt.setObject(2, localDate)
            _ <- stmt.setObject(3, localDateTime)
            _ <- stmt.setObject(4, offsetDateTime)
            count <- stmt.executeUpdate()
          yield count
        rs <- conn.executeQuery("SELECT * FROM ps_jtime WHERE id = 1")
      yield
        assertEquals(insertResult, 1)
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

  test("prepared statement - sql injection protection"):
    val result = DuckDBConnection.withConnection(): conn =>
      for
        _ <- conn.executeUpdate(
          "CREATE TABLE ps_inject (id INTEGER, name VARCHAR)"
        )
        _ <- conn.executeUpdate("INSERT INTO ps_inject VALUES (1, 'Admin')")

        // Attempt SQL injection through parameter
        maliciousInput = "'; DROP TABLE ps_inject; --"

        queryResult <- conn.withPreparedStatement(
          "SELECT * FROM ps_inject WHERE name = ?"
        ): stmt =>
          for
            _ <- stmt.setString(1, maliciousInput)
            rs <- stmt.executeQuery()
          yield
            // Should find no results (injection attempt treated as literal string)
            assert(!rs.next())
            rs.close()

        // Verify table still exists
        rs <- conn.executeQuery("SELECT COUNT(*) as cnt FROM ps_inject")
      yield
        assert(rs.next())
        assertEquals(rs.getInt("cnt"), 1)
        rs.close()

    assert(result.isRight)
