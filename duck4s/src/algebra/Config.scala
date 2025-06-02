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

/** Specifies the connection mode for DuckDB connections.
  *
  * DuckDB supports two primary connection modes: in-memory databases for
  * temporary data processing and persistent databases stored on disk.
  *
  * @since 0.1.0
  */
enum ConnectionMode:
  /** Creates an in-memory database that exists only for the duration of the
    * connection.
    *
    * In-memory databases are ideal for temporary data processing, testing, and
    * scenarios where data persistence is not required.
    */
  case InMemory

  /** Creates a persistent database stored at the specified file path.
    *
    * @param path
    *   The file system path where the database file will be created or opened
    */
  case Persistent(path: String)

/** Configuration options for DuckDB connections.
  *
  * This case class provides a comprehensive set of configuration options for
  * customizing DuckDB connection behavior, including connection mode, read-only
  * access, temporary directory settings, and additional JDBC properties.
  *
  * @param mode
  *   The connection mode (in-memory or persistent)
  * @param readOnly
  *   Whether the connection should be read-only
  * @param tempDirectory
  *   Optional custom temporary directory for DuckDB operations
  * @param streamResults
  *   Whether to enable JDBC result streaming for large result sets
  * @param additionalProperties
  *   Additional JDBC properties to pass to the connection
  *
  * @example
  *   {{{ // In-memory database with streaming enabled val config =
  *   DuckDBConfig( mode = ConnectionMode.InMemory, streamResults = true )
  *
  * // Persistent read-only database val readOnlyConfig = DuckDBConfig( mode =
  * ConnectionMode.Persistent("/path/to/database.db"), readOnly = true ) }}}
  *
  * @see
  *   [[ConnectionMode]] for available connection modes
  * @since 0.1.0
  */
case class DuckDBConfig(
    mode: ConnectionMode = ConnectionMode.InMemory,
    readOnly: Boolean = false,
    tempDirectory: Option[String] = None,
    streamResults: Boolean = false,
    additionalProperties: Map[String, String] = Map.empty
)

/** Factory methods for creating common DuckDB configurations. */
object DuckDBConfig:

  /** Creates a configuration for an in-memory DuckDB database.
    *
    * This is the most common configuration for temporary data processing and
    * testing.
    *
    * @return
    *   A [[DuckDBConfig]] configured for in-memory operation
    * @since 0.1.0
    */
  def inMemory: DuckDBConfig = DuckDBConfig()

  /** Creates a configuration for a persistent DuckDB database.
    *
    * @param path
    *   The file system path where the database will be stored
    * @return
    *   A [[DuckDBConfig]] configured for persistent storage at the specified
    *   path
    * @since 0.1.0
    */
  def persistent(path: String): DuckDBConfig =
    DuckDBConfig(mode = ConnectionMode.Persistent(path))
