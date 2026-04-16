package io.concentric

import scala.jdk.CollectionConverters.*

private[concentric] object JvmValueConversions:

  def deepToScala(value: Any): Any = value match
    case null => null
    case opt: java.util.Optional[?] =>
      if opt.isEmpty then None else Some(deepToScala(opt.get()))
    case m: java.util.Map[?, ?] =>
      m.asInstanceOf[java.util.Map[String, Any]].asScala.iterator
        .map { (k, v) => k -> deepToScala(v) }
        .toMap
    case m: scala.collection.Map[?, ?] =>
      m.asInstanceOf[scala.collection.Map[String, Any]].iterator
        .map { (k, v) => k -> deepToScala(v) }
        .toMap
    case l: java.util.List[?] =>
      l.asScala.toList.map(deepToScala)
    case l: List[?] =>
      l.map(deepToScala)
    case seq: Seq[?] =>
      seq.toList.map(deepToScala)
    case other => other

  def deepToJava(value: Any): AnyRef = value match
    case null => null
    case Some(inner) => deepToJava(inner)
    case None => null
    case opt: java.util.Optional[?] => opt.asInstanceOf[AnyRef]
    case m: scala.collection.Map[?, ?] =>
      val jm = new java.util.LinkedHashMap[String, AnyRef]()
      m.asInstanceOf[scala.collection.Map[String, Any]].foreach { (k, v) =>
        jm.put(k, deepToJava(v))
      }
      jm
    case m: java.util.Map[?, ?] =>
      val jm = new java.util.LinkedHashMap[String, AnyRef]()
      m.asInstanceOf[java.util.Map[String, Any]].asScala.foreach { (k, v) =>
        jm.put(k, deepToJava(v))
      }
      jm
    case l: Iterable[?] =>
      val jl = new java.util.ArrayList[AnyRef]()
      l.foreach(v => jl.add(deepToJava(v)))
      jl
    case l: java.util.List[?] =>
      val jl = new java.util.ArrayList[AnyRef]()
      l.asScala.foreach(v => jl.add(deepToJava(v)))
      jl
    case b: Boolean => java.lang.Boolean.valueOf(b)
    case i: Int => java.lang.Integer.valueOf(i)
    case l: Long => java.lang.Long.valueOf(l)
    case d: Double => java.lang.Double.valueOf(d)
    case f: Float => java.lang.Float.valueOf(f)
    case other => other.asInstanceOf[AnyRef]

  def toScalaRaw(javaMap: java.util.Map[String, AnyRef]): RawObject =
    deepToScala(javaMap).asInstanceOf[RawObject]

  def toJavaRaw(raw: RawObject): java.util.Map[String, AnyRef] =
    deepToJava(raw).asInstanceOf[java.util.Map[String, AnyRef]]
