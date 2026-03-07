// Example script to test locally published duck4s artifacts
// This script demonstrates basic usage of the duck4s library after publishing locally
//> using scala "3.8.2"
//> using repository "ivy2Local"
//> using dep "com.softinio:duck4s_3:0.1.3-5-104ede9" // Update this version to match your locally published version

import com.softinio.duck4s.*
import com.softinio.duck4s.algebra.*

@main def testDuck4s(): Unit = {
  println("Testing duck4s locally published package...")

  // Create a connection
  val connectionResult = DuckDBConnection.connect()
  val connection = connectionResult match {
    case Right(conn) =>
      println("✅ Successfully created in-memory connection")
      conn
    case Left(error) =>
      println(s"❌ Failed to create connection: $error")
      sys.exit(1)
  }

  // Execute a simple query
  connection.executeUpdate("CREATE TABLE test (id INTEGER, name VARCHAR)") match {
    case Right(_) => println("✅ Created table")
    case Left(error) => println(s"❌ Failed to create table: $error")
  }

  // Insert some data
  connection.executeUpdate("INSERT INTO test VALUES (1, 'Alice'), (2, 'Bob')") match {
    case Right(count) => println(s"✅ Inserted $count rows")
    case Left(error) => println(s"❌ Failed to insert: $error")
  }

  // Query data using prepared statement
  connection.withPreparedStatement("SELECT * FROM test") { stmt =>
    stmt.executeQuery().map { rs =>
      println("\nQuery results:")
      while (rs.next()) {
        val id = rs.getInt("id")
        val name = rs.getString("name")
        println(s"  ID: $id, Name: $name")
      }
    }
  } match {
    case Right(_) => println("✅ Basic query completed")
    case Left(error) => println(s"❌ Query failed: $error")
  }

  // Test new 0.1.4 PreparedStatement features: setFloat, setDate, setTimestamp,
  // setObject (UUID), setBigDecimal, setBytes; and ResultSet.getBytes
  println("\nTesting 0.1.4 new type support...")

  connection.executeUpdate("""
    CREATE TABLE events (
      id        INTEGER,
      score     FLOAT,
      price     DECIMAL(10,2),
      recorded  DATE,
      created   TIMESTAMP,
      uid       UUID,
      payload   BLOB
    )
  """) match {
    case Right(_) => println("✅ Created events table")
    case Left(error) => println(s"❌ Failed to create events table: $error")
  }

  val ts    = java.sql.Timestamp.valueOf("2024-06-15 10:30:00")
  val date  = java.sql.Date.valueOf("2024-06-15")
  val uuid  = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
  val bytes = "hello".getBytes("UTF-8")

  connection.withPreparedStatement("INSERT INTO events VALUES (?, ?, ?, ?, ?, ?, ?)") { stmt =>
    for
      _ <- stmt.setInt(1, 1)
      _ <- stmt.setFloat(2, 9.5f)
      _ <- stmt.setBigDecimal(3, BigDecimal("19.99"))
      _ <- stmt.setDate(4, date)
      _ <- stmt.setTimestamp(5, ts)
      _ <- stmt.setObject(6, uuid)
      _ <- stmt.setBytes(7, bytes)
      count <- stmt.executeUpdate()
    yield count
  } match {
    case Right(n) => println(s"✅ Inserted $n row with new types")
    case Left(error) => println(s"❌ Insert with new types failed: $error")
  }

  connection.executeQuery("SELECT * FROM events WHERE id = 1") match {
    case Right(rs) =>
      if rs.next() then
        val score   = rs.getFloat("score")
        val price   = rs.getBigDecimal("price")
        val rec     = rs.getDate("recorded")
        val created = rs.getTimestamp("created")
        val uid     = rs.getObject("uid", classOf[java.util.UUID])
        val blob    = rs.getBytes("payload")
        println(s"  score=$score price=$price recorded=$rec created=$created uid=$uid payload=${new String(blob)}")
        println("✅ Read back all new types successfully")
      rs.close()
    case Left(error) => println(s"❌ Query for new types failed: $error")
  }

  // Test batch with new types (Float, BigDecimal, UUID, Date, Timestamp) - max 6-element tuple
  println("\nTesting 0.1.4 batch with new types...")

  connection.executeUpdate("CREATE TABLE readings (id INTEGER, val FLOAT, dec DECIMAL(10,2), uid UUID, dt DATE, ts TIMESTAMP)") match {
    case Right(_) => ()
    case Left(error) => println(s"❌ Failed to create readings table: $error")
  }

  connection.withBatch("INSERT INTO readings VALUES (?, ?, ?, ?, ?, ?)") { batch =>
    for
      _ <- batch.addBatch((1, 7.5f, BigDecimal("9.99"), uuid, date, ts))
      result <- batch.executeBatch()
    yield result
  } match {
    case Right(r) => println(s"✅ Batch with new types: ${r.successCount} inserted, ${r.failureCount} failed")
    case Left(error) => println(s"❌ Batch with new types failed: $error")
  }

  // Close connection
  connection.close()
  println("\n✅ All tests completed successfully!")
}

// To run this example:
// 1. First publish duck4s locally: mill __.publishLocal
// 2. Check the published version: mill show 'duck4s[3.8.2].publishVersion'
// 3. Update line 5 above with the published version
// 4. Run this script: scala-cli run scripts/example-usage.scala
