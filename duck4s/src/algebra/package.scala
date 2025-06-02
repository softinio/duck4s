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

package com.softinio.duck4s

/** # Algebra Package: Core Types and Data Structures
  *
  * The algebra package contains the fundamental types, error hierarchies, and
  * algebraic data structures that form the foundation of the duck4s library.
  * These types provide type-safe, functional abstractions over DuckDB's JDBC
  * interface.
  *
  * ## Core Types
  *
  * ### Error Handling
  *   - [[DuckDBError]] - Sealed trait hierarchy for all possible errors
  *   - [[DuckDBError.ConnectionError]] - Connection establishment and
  *     management errors
  *   - [[DuckDBError.QueryError]] - SQL execution errors with query context
  *   - [[DuckDBError.TransactionError]] - Transaction-related errors
  *   - [[DuckDBError.ConfigurationError]] - Configuration validation errors
  *   - [[DuckDBError.InvalidStateError]] - Invalid object state errors
  *
  * ### Configuration
  *   - [[DuckDBConfig]] - Comprehensive connection configuration
  *   - [[ConnectionMode]] - In-memory vs persistent database modes
  *
  * ### Query Execution
  *   - [[DuckDBResultSet]] - Wrapper around JDBC ResultSet with resource
  *     management
  *   - [[DuckDBPreparedStatement]] - Type-safe prepared statement wrapper
  *
  * ### Batch Operations
  *   - [[DuckDBBatch]] - Efficient batch operation wrapper
  *   - [[DuckDBBatchResult]] - Results and metrics from batch execution
  *   - [[BatchBinder]] - Type class for binding batch parameters
  *   - [[ParameterBinder]] - Type class for individual parameter binding
  *
  * ## Type Classes for Batch Operations
  *
  * The algebra package provides powerful type classes for type-safe batch
  * operations:
  *
  * {{{
  * // Built-in support for tuples up to 4 elements
  * batch.addBatch((1, "Alice"))                    // (Int, String)
  * batch.addBatch((1, "Alice", 25))                // (Int, String, Int)
  * batch.addBatch((1, "Alice", 25, true))          // (Int, String, Int, Boolean)
  * batch.addBatch((1, "Alice", 25, 999.99))        // (Int, String, Int, Double)
  *
  * // Built-in support for primitive types
  * stmt.setInt(1, 42)
  * stmt.setString(2, "hello")
  * stmt.setDouble(3, 3.14)
  * stmt.setBoolean(4, true)
  * stmt.setLong(5, 1000L)
  *
  * // Option support for nullable values
  * stmt.setString(1, Some("value"))    // Sets the string value
  * stmt.setString(1, None)             // Sets NULL
  * }}}
  *
  * ## Custom Type Class Instances
  *
  * You can provide custom `ParameterBinder` instances for your own types:
  *
  * {{{
  * case class UserId(value: Long)
  *
  * given ParameterBinder[UserId] with
  *   def bind(stmt: DuckDBPreparedStatement, index: Int, value: UserId): Either[DuckDBError, Unit] =
  *     stmt.setLong(index, value.value).map(_ => ())
  *
  * // Now UserId can be used in batch operations
  * batch.addBatch((UserId(123), "Alice"))
  * }}}
  *
  * ## Error Handling Patterns
  *
  * All operations return `Either[DuckDBError, T]` for functional composition:
  *
  * {{{
  * for
  *   stmt <- conn.prepareStatement("INSERT INTO users VALUES (?, ?)")
  *   _ <- stmt.setInt(1, 1)
  *   _ <- stmt.setString(2, "Alice")
  *   result <- stmt.executeUpdate()
  * yield result
  * }}}
  *
  * ## Resource Management
  *
  * Types in this package follow consistent resource management patterns:
  *
  *   - All resource types implement `AutoCloseable`
  *   - Prefer `with*` methods for automatic cleanup
  *   - Resources are cleaned up even on errors
  *   - Nested resource usage is safely composed
  *
  * @see
  *   [[com.softinio.duck4s.DuckDBConnection]] for the main connection interface
  * @since 0.1.0
  */
package object algebra
