package com.infobip.kafkistry.service.consume.filter

import com.infobip.kafkistry.service.consume.JsonPathDef
import java.util.concurrent.ConcurrentHashMap

fun JsonPathParser.parseAsTree(paths: Set<JsonPathDef>): JsonPathsTree {
   return paths
        .map { parseJsonKeyPath(it) }
        .fold(JsonPathsTree()) { acc: JsonPathsTree, path: List<KeyPathElement> ->
            acc.apply { add(path) }
        }
}

class JsonPathsTree {

    private var leaf = false
    private val tree = ConcurrentHashMap<KeyPathElement, JsonPathsTree>()

    fun add(path: List<KeyPathElement>) {
        if (path.isEmpty()) {
            leaf = true
            return
        }
        tree.computeIfAbsent(path.first()) { JsonPathsTree() }.add(path.subList(1, path.size))
    }

    operator fun contains(jsonPath: List<String?>): Boolean {
        if (jsonPath.isEmpty()) {
            return leaf
        }
        val keyCandidates = listOfNotNull(
            jsonPath.first()?.let { MapKeyPathElement(it) },
            MapKeyPathElement(null),
            ListIndexPathElement(null)
        )
        val subPath = jsonPath.subList(1, jsonPath.size)
        return keyCandidates.any { key ->
            val subTree = tree[key] ?: return@any false
            subPath in subTree
        }
    }

    fun replace(value: Any?, replacer: (Any?) -> Any?): Any? {
        if (leaf) {
            return replacer(value)
        }
        return when (value) {
            null -> null
            is Map<*, *> -> replaceMap(value, replacer)
            is List<*> -> replaceList(value, replacer)
            else -> value
        }
    }

    private fun replaceMap(map: Map<*, *>, replacer: (Any?) -> Any?): Map<*, *> {
        var replaced = false
        val replacedMap = map.mapValues { (key, value) ->
            var replacedVal = value
            tree.forEach { (pathElem, subTree) ->
                if (pathElem is MapKeyPathElement && (pathElem == MapKeyPathElement.ALL || key == pathElem.keyName)) {
                    replacedVal = subTree.replace(replacedVal, replacer)
                }
            }
            replacedVal.also { if (replacedVal != value) replaced = true }
        }
        return if (replaced) replacedMap else map
    }

    private fun replaceList(list: List<*>, replacer: (Any?) -> Any?): List<*> {
        var replaced = false
        val replacedList = list.mapIndexed{ index, value ->
            var replacedVal = value
            tree.forEach { (pathElem, subTree) ->
                if (pathElem is ListIndexPathElement && (pathElem == ListIndexPathElement.ALL || index == pathElem.index)) {
                    replacedVal = subTree.replace(replacedVal, replacer)
                }
            }
            replacedVal.also { if (replacedVal != value) replaced = true }
        }
        return if (replaced) replacedList else list
    }

}

