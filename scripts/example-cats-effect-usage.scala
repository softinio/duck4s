// Example script to test locally published duck4s-cats-effect artifacts
// This script demonstrates basic usage of the duck4s-cats-effect library after publishing locally
//> using scala "3.8.2"
//> using repository "ivy2Local"
//> using dep "com.softinio:duck4s_3:0.1.2-4-1bed287"              // Update this version to match your locally published version
//> using dep "com.softinio:duck4s-cats-effect_3:0.1.2-4-1bed287" // Update this version to match your locally published version

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

// To run this example:
// 1. First publish both modules locally: mill __.publishLocal
// 2. Check the published version: mill show 'duck4s[3.8.2].publishVersion'
// 3. Update lines 5-6 above with the published version
// 4. Run this script: scala-cli run scripts/example-cats-effect-usage.scala
