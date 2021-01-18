package com.rcswitchcontrol.zigbee.utilities

import com.rcswitchcontrol.zigbee.models.Value
import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.IeeeAddress
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpoint
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.ZigBeeNode
import com.zsmartsystems.zigbee.zcl.ZclCluster
import com.zsmartsystems.zigbee.zcl.ZclStatus
import com.zsmartsystems.zigbee.zcl.clusters.general.ReadAttributesResponse

fun Array<out String>.expect(expected: Int) {
    if (size != expected) throw IllegalArgumentException("Invalid number of command arguments, expected $expected, got $size")
}

fun ZigBeeNode.addressOf(endpoint: ZigBeeEndpoint) =
        "$networkAddress/${endpoint.endpointId}"

inline fun <reified T : ZclCluster> ZigBeeNetworkManager.trifecta(lookUpId: String, clusterId: Int): Triple<ZigBeeNode, ZigBeeEndpoint, T> {
    val destination = findDestination(lookUpId)
    if (destination !is ZigBeeEndpointAddress) throw Exception("This is not a ZigBee Endpoint Address")

    val node = getNode(destination.address)
    val endpoint = node.getEndpoint(destination.endpoint)
            ?: throw Exception("Unable to find end point")

    val cluster = endpoint.getInputCluster(clusterId) as T

    return Triple(node, endpoint, cluster)
}

/**
 * Gets device by device identifier.
 *
 * @param deviceIdentifier the device identifier
 * @return the device
 */

fun ZigBeeNetworkManager.findDevice(deviceIdentifier: String): ZigBeeEndpoint? = try {
    this.getEndpoint(deviceIdentifier)
} catch (e: Exception) {
    null
}

fun ZclCluster.pullAttributes(nodeAddress: String, attributeIds: List<Int>): List<ZigBeeAttribute> =
        when (val response = readAttributes(attributeIds)
                .get()
                .takeIf(CommandResult::isSuccess)
                ?.getResponse<ReadAttributesResponse>()) {
            null -> listOf()
            else -> response.records
                    .filter { it.status == ZclStatus.SUCCESS }
                    .map {
                        ZigBeeAttribute(
                                nodeAddress = nodeAddress,
                                attributeId = it.attributeIdentifier,
                                endpointId = zigBeeAddress.endpoint,
                                clusterId = clusterId,
                                type = it.attributeDataType.dataClass.simpleName,
                                value = when(val value = it.attributeValue) {
                                    is Int -> Value.Int(value)
                                    is Float -> Value.Float(value)
                                    is Boolean -> Value.Boolean(value)
                                    else -> throw IllegalArgumentException("Unknown value type")
                                }
                        )
                    }
        }

/**
 * Gets destination by device identifier or group ID.
 *
 * @param destinationIdentifier the device identifier or group ID
 * @return the device
 */
fun ZigBeeNetworkManager.findDestination(destinationIdentifier: String): ZigBeeAddress =
        when (val device = findDevice(destinationIdentifier)) {
            null -> try {
                groups.firstOrNull { destinationIdentifier == it.label }
                        ?: getGroup(destinationIdentifier.toInt())
            } catch (e: Exception) {
                throw Exception("Unable to find device. Error  message: ${e.message}")
            }
            else -> device.endpointAddress
        }

/**
 * Gets a [ZigBeeNode]
 *
 * @param this@getNode the [ZigBeeNetworkManager]
 * @param nodeId a [String] with the node Id
 * @return the [ZigBeeNode]
 * @throws IllegalArgumentException
 */
@Throws(IllegalArgumentException::class)
fun ZigBeeNetworkManager.getNode(nodeId: String): ZigBeeNode {
    try {
        val nwkAddress = nodeId.toInt()
        if (getNode(nwkAddress) != null) return getNode(nwkAddress)
    } catch (e: Exception) {
    }
    try {
        val ieeeAddress = IeeeAddress(nodeId)
        if (getNode(ieeeAddress) != null) return getNode(ieeeAddress)
    } catch (e: Exception) {
    }
    throw IllegalArgumentException("Node '$nodeId' is not found.")
}

/**
 * Gets [ZigBeeEndpoint] by device identifier.
 *
 * @param endpointId the device identifier
 * @return the [ZigBeeEndpoint]
 * @throws IllegalArgumentException
 */
@Throws(IllegalArgumentException::class)
fun ZigBeeNetworkManager.getEndpoint(endpointId: String): ZigBeeEndpoint {
    for (node in nodes)
        for (endpoint in node.endpoints)
            if (endpointId == node.networkAddress.toString() + "/" + endpoint.endpointId)
                return endpoint
    throw IllegalArgumentException("Endpoint '$endpointId' is not found")
}

/**
 * Parses a cluster ID as a string to an integer. The ID can be either a decimal or a hexadecimal literal (e..g, 11
 * or 0xB).
 *
 * @param clusterId a [String] name of the cluster
 * @return the cluster ID as an integer
 * @throws IllegalArgumentException
 */
@Throws(IllegalArgumentException::class)
fun parseClusterId(clusterId: String): Int = try {
    getInteger(clusterId)
} catch (e: NumberFormatException) {
    throw IllegalArgumentException("Cluster ID '$clusterId' uses an invalid number format.")
}

/**
 * Gets the cluster for a given endpoint, where the cluster is specified by a cluster specifier.
 *
 *
 * The cluster specifier consists of the cluster ID (either in decimal, or in hex prefixed with 0x), optionally
 * prepended with any of the prefixes 'in', 'out', 'client', or 'server'. The prefix indicates whether an input or
 * an output cluster shall be returned. If no prefix is provided, then the method first tries to return an input
 * cluster with the given id, and, if none is found, to return an output cluster.
 *
 *
 * Examples for cluster specifiers:
 *
 *  * 0x0B
 *  * 11
 *  * in:0xB
 *  * server:11
 *
 *
 * @param clusterSpecifier a cluster specified as described above (must be non-null)
 * @return the specified cluster provided by the endpoint or null if no such cluster is found
 * @throws IllegalArgumentException if the clusterSpecifier uses an invalid number format, or if no cluster is found
 */
@Throws(IllegalArgumentException::class)
fun ZigBeeEndpoint.findCluster(clusterSpecifier: String): ZclCluster {
    val isInput: Boolean
    val isOutput: Boolean
    val clusterIdString: String
    if (clusterSpecifier.contains(":")) {
        val prefix = clusterSpecifier.substring(0, clusterSpecifier.indexOf(':'))
        isInput = prefix.equals("in", ignoreCase = true) || prefix.equals("server", ignoreCase = true)
        isOutput = prefix.equals("out", ignoreCase = true) || prefix.equals("client", ignoreCase = true)
        require(isInput || isOutput) {
            ("The prefix of the cluster specifier must be 'in', 'out', 'server', or 'client', but it was: "
                    + prefix)
        }
        clusterIdString = clusterSpecifier.substring(clusterSpecifier.indexOf(':') + 1)
    } else {
        isInput = false
        isOutput = false
        clusterIdString = clusterSpecifier
    }
    val clusterId = parseClusterId(clusterIdString)
    val result: ZclCluster?
    result = if (isInput) {
        getInputCluster(clusterId)
    } else if (isOutput) {
        getOutputCluster(clusterId)
    } else {
        val cluster = getInputCluster(clusterId)
        cluster ?: getOutputCluster(clusterId)
    }
    return result
            ?: throw IllegalArgumentException("A cluster specified by " + clusterSpecifier
                    + " is not found for endpoint " + endpointId)
}

private fun getInteger(string: String): Int =
        if (string.startsWith("0x")) string.substring(2).toInt(16)
        else string.toInt()