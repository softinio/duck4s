// Example script to test locally published duck4s-cats-effect artifacts
// This script demonstrates basic usage of the duck4s-cats-effect library after publishing locally
//> using scala "3.8.2"
//> using repository "ivy2Local"
//> using dep "com.softinio:duck4s_3:0.1.3-5-104ede9"              // Update this version to match your locally published version
//> using dep "com.softinio:duck4s-cats-effect_3:0.1.3-5-104ede9" // Update this version to match your locally published version

import cats.effect.{IO, IOApp}
import com.softinio.duck4s.effect.*
import com.softinio.duck4s.algebra.*

object TestDuck4sCatsEffect extends IOApp.Simple:

  def run: IO[Unit] =
    IO.println("Testing duck4s-cats-effect locally published package...") >>
      testConnection >>
      testStream >>
      testTransaction >>
      testRollback >>
      testNewTypes >>
      testBatchNewTypes >>
      IO.println("\nAll tests passed!")

  def testConnection: IO[Unit] =
    DuckDBIO.connect().use { conn =>
      for
        _ <- IO(assert(!conn.isClosed(), "connection should be open"))
        _ <- IO.println("Successfully opened Resource-managed connection")
        _ <- conn.executeUpdateIO("CREATE TABLE test (id INTEGER, name VARCHAR)")
        _ <- IO.println("Created table")
        count <- conn.executeUpdateIO("INSERT INTO test VALUES (1, 'Alice'), (2, 'Bob')")
        _ <- IO.println(s"Inserted $count rows")
        rs <- conn.executeQueryIO("SELECT * FROM test ORDER BY id")
        _ <- IO:
          assert(rs.next(), "expected first row")
          assert(rs.getInt("id") == 1)
          assert(rs.getString("name") == "Alice")
          assert(rs.next(), "expected second row")
          assert(rs.getInt("id") == 2)
          assert(rs.getString("name") == "Bob")
          rs.close()
        _ <- IO.println("executeUpdateIO / executeQueryIO round-trip OK")
      yield ()
    }

  def testStream: IO[Unit] =
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("CREATE TABLE nums (n INTEGER)")
        _ <- conn.executeUpdateIO("INSERT INTO nums VALUES (1),(2),(3)")
        rows <- DuckDBIO.stream(conn, "SELECT n FROM nums ORDER BY n")(_.getInt("n")).compile.toList
        _ <- IO(assert(rows == List(1, 2, 3), s"expected List(1,2,3) but got $rows"))
        _ <- IO.println(s"Stream emitted all rows: $rows")
      yield ()
    }

  def testTransaction: IO[Unit] =
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("CREATE TABLE tx_test (v INTEGER)")
        _ <- conn.withTransactionIO { txConn =>
          txConn.executeUpdateIO("INSERT INTO tx_test VALUES (42)")
        }
        rs <- conn.executeQueryIO("SELECT v FROM tx_test")
        _ <- IO:
          assert(rs.next(), "expected a row after commit")
          assert(rs.getInt("v") == 42, "expected value 42")
          rs.close()
        _ <- IO.println("withTransactionIO commits on success OK")
      yield ()
    }

  def testRollback: IO[Unit] =
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("CREATE TABLE rollback_test (v INTEGER)")
        _ <- conn.withTransactionIO { txConn =>
          txConn.executeUpdateIO("INSERT INTO rollback_test VALUES (99)") >>
            IO.raiseError(new RuntimeException("intentional rollback"))
        }.attempt
        rs <- conn.executeQueryIO("SELECT COUNT(*) FROM rollback_test")
        _ <- IO:
          assert(rs.next())
          assert(rs.getInt(1) == 0, "expected 0 rows after rollback")
          rs.close()
        _ <- IO.println("withTransactionIO rolls back on error OK")
      yield ()
    }

  // New in 0.1.4: setFloat, setDate, setTimestamp, setObject (UUID),
  // setBigDecimal, setBytes; and ResultSet.getBytes
  def testNewTypes: IO[Unit] =
    DuckDBIO.connect().use { conn =>
      val ts    = java.sql.Timestamp.valueOf("2024-06-15 10:30:00")
      val date  = java.sql.Date.valueOf("2024-06-15")
      val uuid  = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
      val bytes = "hello".getBytes("UTF-8")

      for
        _ <- conn.executeUpdateIO("""
          CREATE TABLE events (
            id       INTEGER,
            score    FLOAT,
            price    DECIMAL(10,2),
            recorded DATE,
            created  TIMESTAMP,
            uid      UUID,
            payload  BLOB
          )
        """)
        _ <- conn.withPreparedStatementIO("INSERT INTO events VALUES (?, ?, ?, ?, ?, ?, ?)") { stmt =>
          for
            _ <- IO.blocking(stmt.setInt(1, 1))
            _ <- IO.blocking(stmt.setFloat(2, 9.5f))
            _ <- IO.blocking(stmt.setBigDecimal(3, BigDecimal("19.99")))
            _ <- IO.blocking(stmt.setDate(4, date))
            _ <- IO.blocking(stmt.setTimestamp(5, ts))
            _ <- IO.blocking(stmt.setObject(6, uuid))
            _ <- IO.blocking(stmt.setBytes(7, bytes))
            n <- IO.blocking(stmt.executeUpdate())
          yield n
        }
        rs <- conn.executeQueryIO("SELECT * FROM events WHERE id = 1")
        _ <- IO:
          assert(rs.next(), "expected a row")
          assert(rs.getFloat("score") == 9.5f)
          val blob = rs.getBytes("payload")
          assert(new String(blob) == "hello", s"expected 'hello' blob, got '${new String(blob)}'")
          rs.close()
        _ <- IO.println("0.1.4 new PreparedStatement types (setFloat/setDate/setTimestamp/setObject/setBigDecimal/setBytes) and ResultSet.getBytes OK")
      yield ()
    }

  // New in 0.1.4: batch binders for Float, BigDecimal, UUID, Date, Timestamp, Blob
  def testBatchNewTypes: IO[Unit] =
    DuckDBIO.connect().use { conn =>
      val ts    = java.sql.Timestamp.valueOf("2024-01-01 00:00:00")
      val date  = java.sql.Date.valueOf("2024-01-01")
      val uuid  = java.util.UUID.randomUUID()
      val bytes = "data".getBytes("UTF-8")

      for
        _ <- conn.executeUpdateIO("CREATE TABLE readings (id INTEGER, ts TIMESTAMP, uid UUID, val FLOAT, dec DECIMAL(10,2), data BLOB)")
        result <- conn.withBatchIO("INSERT INTO readings VALUES (?, ?, ?, ?, ?, ?)") { batch =>
          for
            _ <- IO.blocking(batch.addBatch((1, ts, uuid, 1.5f, BigDecimal("9.99"), bytes)))
            r <- IO.blocking(batch.executeBatch()).flatMap {
              case Right(r) => IO.pure(r)
              case Left(e)  => IO.raiseError(new RuntimeException(e.toString))
            }
          yield r
        }
        _ <- IO(assert(result.successCount == 1, s"expected 1 success, got ${result.successCount}"))
        _ <- IO.println(s"0.1.4 batch with new types OK: ${result.successCount} inserted")
      yield ()
    }

// To run this example:
// 1. First publish both modules locally: mill __.publishLocal
// 2. Check the published version: mill show 'duck4s[3.8.2].publishVersion'
// 3. Update lines 5-6 above with the published version
// 4. Run this script: scala-cli run scripts/example-cats-effect-usage.scala
