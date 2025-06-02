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

import java.sql.ResultSet

/** A wrapper around a JDBC ResultSet that provides automatic resource
  * management for both the result set and its associated statement.
  *
  * This class is designed to be used with the using pattern or explicit
  * resource management to ensure that both the underlying ResultSet and its
  * Statement are properly closed when no longer needed. The class exports
  * common ResultSet methods for convenient access.
  *
  * @param underlying
  *   the underlying JDBC ResultSet
  * @param statement
  *   the Statement that created this ResultSet, needed for proper cleanup
  *
  * @example
  *   {{{ import scala.util.Using
  *
  * // Automatic resource management val result =
  * Using(connection.executeQuery("SELECT * FROM users")) { rs => while
  * (rs.next()) { println(s"Name: ${rs.getString("name")}, Age:
  * ${rs.getInt("age")}") } }
  *
  * // Or with explicit management val rs = connection.executeQuery("SELECT
  * count(*) FROM users") try { if (rs.next()) { val count = rs.getInt(1)
  * println(s"Total users: $count") } } finally { rs.close() // Closes both
  * ResultSet and Statement } }}}
  *
  * @see
  *   [[DuckDBConnection]] for creating result sets
  * @see
  *   [[DuckDBPreparedStatement]] for parameterized queries
  * @since 0.1.0
  */
case class DuckDBResultSet(
    private val underlying: ResultSet,
    private val statement: java.sql.Statement
) extends AutoCloseable:

  /** Exports commonly used ResultSet methods for direct access.
    *
    * These methods are available directly on the DuckDBResultSet instance:
    *   - `next()`: Move cursor to next row
    *   - `getString(columnIndex/columnLabel)`: Get string value
    *   - `getInt(columnIndex/columnLabel)`: Get integer value
    *   - `getLong(columnIndex/columnLabel)`: Get long value
    *   - `getDouble(columnIndex/columnLabel)`: Get double value
    *   - `getBoolean(columnIndex/columnLabel)`: Get boolean value
    *   - `wasNull()`: Check if last retrieved value was NULL
    *
    * @since 0.1.0
    */
  export underlying.{
    next,
    getString,
    getInt,
    getLong,
    getDouble,
    getBoolean,
    wasNull
  }

  /** Closes both the underlying ResultSet and its associated Statement.
    *
    * This method ensures proper cleanup of database resources. It's important
    * to call this method when done with the result set, either explicitly or
    * through a resource management pattern like `Using`.
    *
    * @example
    *   {{{ val rs = connection.executeQuery("SELECT * FROM users") try { //
    *   Process results... } finally { rs.close() // Always close resources }
    *   }}}
    *
    * @since 0.1.0
    */
  def close(): Unit =
    underlying.close()
    statement.close()
