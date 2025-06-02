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

/** Result of executing a batch operation, containing update counts and
  * statistics.
  *
  * This class encapsulates the results of executing a batch operation,
  * providing both detailed update counts for each operation in the batch and
  * summary statistics about successes and failures.
  *
  * @param updateCounts
  *   array of update counts for each operation in the batch. Non-negative
  *   values indicate the number of rows affected by that operation. Negative
  *   values indicate failures.
  * @param successCount
  *   the number of operations that completed successfully
  * @param failureCount
  *   the number of operations that failed
  *
  * @example
  *   {{{ val result = for { batch <- Right(DuckDBBatch(stmt)) _ <-
  *   batch.addBatch(("Alice", 25), ("Bob", 30), ("Charlie", 35)) result <-
  *   batch.executeBatch() } yield result
  *
  * result match { case Right(batchResult) => println(s"Total operations:
  * ${batchResult.updateCounts.length}") println(s"Successful:
  * ${batchResult.successCount}") println(s"Failed:
  * ${batchResult.failureCount}")
  *
  * // Check individual operation results
  * batchResult.updateCounts.zipWithIndex.foreach { case (count, index) => if
  * (count >= 0) { println(s"Operation $index: $count rows affected") } else {
  * println(s"Operation $index: failed") } }
  *
  * // Check if all operations succeeded if (batchResult.isAllSuccessful) {
  * println("All operations completed successfully!") }
  *
  * case Left(error) => println(s"Batch execution failed: $error") } }}}
  *
  * @see
  *   [[DuckDBBatch.executeBatch]] for executing batch operations
  * @see
  *   [[DuckDBBatch]] for batch operation management
  * @since 0.1.0
  */
case class DuckDBBatchResult(
    updateCounts: Array[Int],
    successCount: Int,
    failureCount: Int
):

  /** Returns true if all operations in the batch completed successfully.
    *
    * @return
    *   true if failureCount is 0, false otherwise
    *
    * @example
    *   {{{ if (batchResult.isAllSuccessful) { println("Batch completed without
    *   errors") } else { println(s"${batchResult.failureCount} operations
    *   failed") } }}}
    *
    * @since 0.1.0
    */
  def isAllSuccessful: Boolean = failureCount == 0

  /** Returns the total number of operations in the batch.
    *
    * @return
    *   the length of the updateCounts array
    *
    * @example
    *   {{{println(s"Executed ${batchResult.totalOperations} operations")}}}
    *
    * @since 0.1.0
    */
  def totalOperations: Int = updateCounts.length

  /** Returns the total number of rows affected by all successful operations.
    *
    * This sums up all non-negative update counts, giving the total number of
    * rows that were affected by the entire batch operation.
    *
    * @return
    *   the sum of all successful update counts
    *
    * @example
    *   {{{ println(s"Total rows affected: ${batchResult.totalRowsAffected}")
    *   }}}
    *
    * @since 0.1.0
    */
  def totalRowsAffected: Int = updateCounts.filter(_ >= 0).sum

  /** Returns a list of indices for operations that failed.
    *
    * @return
    *   indices of operations with negative update counts
    *
    * @example
    *   {{{ val failedIndices = batchResult.failedOperationIndices if
    *   (failedIndices.nonEmpty) { println(s"Operations that failed:
    *   ${failedIndices.mkString(", ")}") } }}}
    *
    * @since 0.1.0
    */
  def failedOperationIndices: List[Int] =
    updateCounts.zipWithIndex.filter(_._1 < 0).map(_._2).toList
