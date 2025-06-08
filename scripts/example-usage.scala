// Example script to test locally published duck4s artifacts
// This script demonstrates basic usage of the duck4s library after publishing locally
//> using scala "3.7.0"
//> using repository "ivy2Local"
//> using dep "com.softinio:duck4s_3.7.0:0.0.0-6-7132b5-DIRTY594c25f5" // Update this version to match your locally published version

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
    case Right(_) => println("✅ Query completed")
    case Left(error) => println(s"❌ Query failed: $error")
  }

  // Close connection
  connection.close()
  println("\n✅ Test completed successfully!")
}

// To run this example:
// 1. First publish duck4s locally: mill __.publishLocal
// 2. Check the published version: mill show 'duck4s[3.7.0].publishVersion'
// 3. Update line 5 above with the published version
// 4. Run this script: scala-cli run scripts/example-usage.scala
