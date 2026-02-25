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

package com.softinio.duck4s.effect

import munit.CatsEffectSuite
import cats.effect.IO
import com.softinio.duck4s.algebra.{DuckDBConfig, DuckDBError}

class DuckDBIOTest extends CatsEffectSuite:

  test("Resource opens and closes connection") {
    DuckDBIO.connect().use { conn =>
      IO(assert(!conn.isClosed()))
    }
  }

  test("executeUpdateIO / executeQueryIO round-trip") {
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("CREATE TABLE test (id INTEGER, name VARCHAR)")
        _ <- conn.executeUpdateIO("INSERT INTO test VALUES (1, 'Alice')")
        rs <- conn.executeQueryIO("SELECT * FROM test")
        _ <- IO(assert(rs.next()))
        _ <- IO(assertEquals(rs.getInt("id"), 1))
        _ <- IO(assertEquals(rs.getString("name"), "Alice"))
        _ <- IO(rs.close())
      yield ()
    }
  }

  test("stream emits all rows") {
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("CREATE TABLE nums (n INTEGER)")
        _ <- conn.executeUpdateIO("INSERT INTO nums VALUES (1),(2),(3)")
        rows <- DuckDBIO.stream(conn, "SELECT n FROM nums")(_.getInt("n")).compile.toList
        _ <- IO(assertEquals(rows, List(1, 2, 3)))
      yield ()
    }
  }

  test("withTransactionIO commits on success") {
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("CREATE TABLE tx_test (v INTEGER)")
        _ <- conn.withTransactionIO { txConn =>
          txConn.executeUpdateIO("INSERT INTO tx_test VALUES (42)")
        }
        rs <- conn.executeQueryIO("SELECT v FROM tx_test")
        _ <- IO(assert(rs.next()))
        _ <- IO(assertEquals(rs.getInt("v"), 42))
        _ <- IO(rs.close())
      yield ()
    }
  }

  test("withTransactionIO rolls back on error") {
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("CREATE TABLE rollback_test (v INTEGER)")
        _ <- conn.withTransactionIO { txConn =>
          txConn.executeUpdateIO("INSERT INTO rollback_test VALUES (99)") >>
            IO.raiseError(new RuntimeException("intentional rollback"))
        }.attempt
        rs <- conn.executeQueryIO("SELECT COUNT(*) FROM rollback_test")
        _ <- IO(assert(rs.next()))
        _ <- IO(assertEquals(rs.getInt(1), 0))
        _ <- IO(rs.close())
      yield ()
    }
  }

  test("DuckDBException wraps ConnectionError") {
    val error = DuckDBError.ConnectionError("test connection error")
    val ex    = DuckDBException.from(error)
    assertEquals(ex.getMessage, "test connection error")
    assertEquals(ex.error, error)
    assert(ex.getCause == null)
  }

  test("DuckDBException wraps QueryError with cause") {
    val cause = new RuntimeException("jdbc failure")
    val error = DuckDBError.QueryError("query failed", "SELECT 1", Some(cause))
    val ex    = DuckDBException.from(error)
    assertEquals(ex.getMessage, "query failed")
    assertEquals(ex.error, error)
    assertEquals(ex.getCause, cause)
  }
