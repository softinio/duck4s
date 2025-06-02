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
