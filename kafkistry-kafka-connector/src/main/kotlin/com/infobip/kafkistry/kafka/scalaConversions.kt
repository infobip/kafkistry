package com.infobip.kafkistry.kafka

import scala.jdk.CollectionConverters

fun <T> scala.collection.Iterable<T>.toJavaList(): List<T> =
    CollectionConverters.IterableHasAsJava(this).asJava().toList()

fun <T> Iterable<T>.toScalaList(): scala.collection.immutable.List<T> =
    CollectionConverters.IterableHasAsScala(this).asScala().toList()

fun <K, V> Map<K, V>.toScalaMap(): scala.collection.Map<K, V> =
    CollectionConverters.MapHasAsScala(this).asScala()

fun <K, V> scala.collection.Map<K, V>.toJavaMap(): Map<K, V> =
    CollectionConverters.MapHasAsJava(this).asJava()
