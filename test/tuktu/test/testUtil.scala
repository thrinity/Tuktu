package tuktu.test

import tuktu.api.DataPacket
import play.api.libs.json._

object testUtil {
    /**
     * Inspects two lists of DataPackets
     */
    def inspectDataPacketList(obtained: List[DataPacket], expected: List[DataPacket], ignoreOrder: Boolean): Boolean = {
        // Size should be the same
        obtained.size == expected.size && {
            if (ignoreOrder) {
                def helper(obtainedRemaining: List[DataPacket], expectedRemaining: List[DataPacket]): Boolean = {
                    obtainedRemaining match {
                        // Since we have already established equally sized lists, we are done once one is empty
                        case Nil => true
                        case head :: tail =>
                            // Find matching DataPacket in expected for head
                            val index = expectedRemaining.indexWhere { dp => inspectDataPacket(head, dp, ignoreOrder) }
                            if (index == -1)
                                // No match found => false
                                false
                            else
                                // Found match; remove index from expectedRemaining and continue with tail
                                helper(tail, expectedRemaining.take(index) ++ expectedRemaining.drop(index + 1))
                    }
                }
                // Order DataPackets after their maps.toSeq.sortBy(_._1) to make it more likely to find the matching DP sooner in helper
                helper(
                    obtained.sortBy(_.data.map(_.toSeq.sortBy(_._1).toString).toString),
                    expected.sortBy(_.data.map(_.toSeq.sortBy(_._1).toString).toString))
            } else {
                obtained.zip(expected).forall { case (dp1, dp2) => inspectDataPacket(dp1, dp2, ignoreOrder) }
            }
        }
    }

    /**
     * Inspects two DataPackets
     */
    def inspectDataPacket(obtained: DataPacket, expected: DataPacket, ignoreOrder: Boolean): Boolean = {
        // Size should be the same
        obtained.size == expected.size && {
            if (ignoreOrder) {
                def helper(obtainedRemaining: List[Map[String, Any]], expectedRemaining: List[Map[String, Any]]): Boolean = {
                    obtainedRemaining match {
                        // Since we have already established equally sized lists, we are done once one is empty
                        case Nil => true
                        case head :: tail =>
                            // Find matching datum in expected for head
                            val index = expectedRemaining.indexWhere { datum => inspectMaps(head, datum) }
                            if (index == -1)
                                // No match found => false
                                false
                            else
                                // Found match; remove index from expectedRemaining and continue with tail
                                helper(tail, expectedRemaining.take(index) ++ expectedRemaining.drop(index + 1))
                    }
                }
                // Order datums after their _.toSeq.sortBy(_._1) to make it more likely to find matching datum sooner in helper
                helper(
                    obtained.data.sortBy(_.toSeq.sortBy(_._1).toString),
                    expected.data.sortBy(_.toSeq.sortBy(_._1).toString))
            } else {
                obtained.data.zip(expected.data).forall { case (d1, d2) => inspectMaps(d1, d2) }
            }
        }
    }

    /**
     * Inspects and matches an obtained map with an expected map
     */
    def inspectMaps(obtained: Map[String, Any], expected: Map[String, Any]): Boolean = {
        // Check keys first
        if (obtained.keySet.equals(expected.keySet)) {
            // Keys match, inspect all values
            (for ((key, value) <- obtained) yield {
                inspectValue(value, expected(key))
            }).foldLeft(true)(_ && _)
        } else
            false
    }

    /**
     * Function to inspect a single value
     */
    def inspectValue(obtained: Any, expected: Any): Boolean = {
        // Check types first
        try {
            obtained match {
                case v: Map[Any, Any] => {
                    val w = expected.asInstanceOf[Map[Any, Any]]
                    v.keys.toList.diff(w.keys.toList).isEmpty && v.forall(elem => inspectValue(elem._2, w(elem._1)))
                }
                case v: List[Any] => {
                    val w = expected.asInstanceOf[List[Any]]
                    v.zip(w).forall(elems => inspectValue(elems._1, elems._2))
                }
                // Rounding errors
                case v: Double => Math.abs(v - expected.asInstanceOf[Double]) < 0.000000001
                case _: Any => expected match {
                    // Rounding errors
                    case v: Double => Math.abs(v - obtained.asInstanceOf[Double]) < 0.000000001
                    case _: Any    => obtained.toString == expected.toString
                }
            }
        } catch {
            // TODO: Maybe differentiate on types of exceptions?
            case e: Throwable => {
                play.api.Logger.error("testUtil.inspectValue", e)
                false
            }
        }
    }

    /**
     * Inspects and matches an obtained JsValue with an expected JsValue
     */
    def inspectJsValue(obtained: JsValue, expected: JsValue, ignoreOrder: Boolean): Boolean = {
        (obtained, expected) match {
            case (obtained: JsNumber, expected: JsNumber)       => obtained.value == expected.value
            case (obtained: JsString, expected: JsString)       => obtained.value == expected.value
            case (obtained: JsBoolean, expected: JsBoolean)     => obtained.value == expected.value
            case (obtained: JsUndefined, expected: JsUndefined) => obtained.error == expected.error
            case (JsNull, JsNull)                               => true
            case (obtained: JsObject, expected: JsObject) => {
                if (obtained.keys.equals(expected.keys)) {
                    obtained.keys.forall { key => inspectJsValue(obtained \ key, expected \ key, ignoreOrder) }
                } else
                    false
            }
            case (obtained: JsArray, expected: JsArray) => {
                obtained.value.size == expected.value.size && {
                    if (ignoreOrder) {
                        def helper(s1: List[JsValue], s2: List[JsValue]): Boolean = s1 match {
                            case Nil => true
                            case head :: tail => {
                                val index = s2.indexWhere(value => inspectJsValue(head, value, ignoreOrder))
                                if (index == -1)
                                    false
                                else
                                    helper(tail, s2.take(index) ++ s2.drop(index + 1))
                            }
                        }
                        helper(obtained.value.toList.sortBy(_.toString), expected.value.toList.sortBy(_.toString))
                    } else {
                        obtained.value.zip(expected.value).forall { case (v1, v2) => inspectJsValue(v1, v2, ignoreOrder) }
                    }
                }
            }
            case (_, _) => false
        }
    }
}