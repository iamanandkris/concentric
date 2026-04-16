package io.concentric

import io.concentric.annotations.contract
import scala.jdk.CollectionConverters.*

private[concentric] final class JvmNestedSupport(
  val decoder: Any => Option[Any],
  val collectFn: (Any, FieldPath) => List[Violation],
  val patchCollectFn: (Any, Any, FieldPath) => List[Violation],
  val sanitizeFn: Any => Any,
  val schema: RawObject,
  val toJavaValueFn: Any => AnyRef,
  val toRawValueFn: Any => Any
):
  def collect(rawValue: Any, basePath: FieldPath): List[Violation] =
    collectFn(rawValue, basePath)

  def patchCollect(currentValue: Any, patchValue: Any, basePath: FieldPath): List[Violation] =
    patchCollectFn(currentValue, patchValue, basePath)

  def sanitize(rawValue: Any): Any =
    sanitizeFn(rawValue)

  def toJavaValue(value: Any): AnyRef =
    toJavaValueFn(value)

  def toRawValue(value: Any): Any =
    toRawValueFn(value)

object JvmNestedSupport:

  private val resolving = new ThreadLocal[Set[Class[?]]]:
    override def initialValue(): Set[Class[?]] = Set.empty

  def forType(javaType: java.lang.reflect.Type): Option[JvmNestedSupport] =
    javaType match
      case c: Class[?] =>
        directForClass(c)
      case pt: java.lang.reflect.ParameterizedType =>
        val raw = pt.getRawType.asInstanceOf[Class[?]]
        if raw == classOf[java.util.Optional[?]] then
          forType(pt.getActualTypeArguments()(0))
        else if raw == classOf[java.util.List[?]] then
          forListType(pt.getActualTypeArguments()(0))
        else None
      case _ => None

  private[concentric] def forClass(clazz: Class[?]): Option[JvmNestedSupport] =
    directForClass(clazz)

  private def directForClass(clazz: Class[?]): Option[JvmNestedSupport] =
    nestedContract(clazz).map { nested =>
      JvmNestedSupport(
        decoder = {
          case null => None
          case value if clazz.isInstance(value.asInstanceOf[AnyRef]) => Some(value)
          case value => toNestedRaw(nested, clazz, value)
        },
        collectFn = (rawValue, basePath) =>
          toNestedRaw(nested, clazz, rawValue)
            .map(nested.collectViolations)
            .getOrElse(Nil)
            .map(prefix(basePath, _)),
        patchCollectFn = (currentValue, patchValue, basePath) =>
          val result =
            (toNestedRaw(nested, clazz, currentValue), toNestedRaw(nested, clazz, patchValue)) match
              case (_, None) => Nil
              case (Some(current), Some(patch)) => nested.collectPatchViolations(current, patch)
              case (None, Some(patch)) => nested.collectViolations(patch)
          result.map(prefix(basePath, _)),
        sanitizeFn = rawValue =>
          toNestedRaw(nested, clazz, rawValue)
            .map(nested.sanitizeRaw)
            .getOrElse(rawValue),
        schema = JvmValueConversions.deepToScala(nested.jsonSchema()).asInstanceOf[RawObject],
        toJavaValueFn = value =>
          value match
            case null => null
            case typed if clazz.isInstance(typed.asInstanceOf[AnyRef]) => typed.asInstanceOf[AnyRef]
            case other =>
              toNestedRaw(nested, clazz, other)
                .map(nested.constructTrusted)
                .getOrElse(throw new IllegalArgumentException(s"Unable to construct nested value for ${clazz.getName}")),
        toRawValueFn = value =>
          value match
            case null => null
            case typed if clazz.isInstance(typed.asInstanceOf[AnyRef]) =>
              JvmValueConversions.deepToScala(nested.toRaw(typed.asInstanceOf[AnyRef]))
            case other =>
              toNestedRaw(nested, clazz, other).getOrElse(other)
      )
    }

  private def forListType(elemType: java.lang.reflect.Type): Option[JvmNestedSupport] =
    forType(elemType).map { elemSupport =>
      JvmNestedSupport(
        decoder = {
          case null => None
          case value =>
            toScalaSeq(value).map(_.map(elem => elemSupport.decoder(elem).getOrElse(elem)))
        },
        collectFn = (rawValue, basePath) =>
          toScalaSeq(rawValue)
            .getOrElse(Nil)
            .zipWithIndex
            .flatMap { case (elem, idx) =>
              elemSupport.collect(elem, basePath / idx)
            },
        patchCollectFn = (currentValue, patchValue, basePath) =>
          // List patching is replace-whole-field semantics today.
          toScalaSeq(patchValue)
            .getOrElse(Nil)
            .zipWithIndex
            .flatMap { case (elem, idx) =>
              elemSupport.collect(elem, basePath / idx)
            },
        sanitizeFn = rawValue =>
          toScalaSeq(rawValue)
            .map(_.map(elemSupport.sanitize))
            .getOrElse(rawValue),
        schema = elemSupport.schema,
        toJavaValueFn = value =>
          val out = new java.util.ArrayList[AnyRef]()
          toScalaSeq(value).getOrElse(Nil).foreach(elem => out.add(elemSupport.toJavaValue(elem)))
          out,
        toRawValueFn = value =>
          toScalaSeq(value).map(_.map(elemSupport.toRawValue)).getOrElse(value)
      )
    }

  private def nestedContract(clazz: Class[?]): Option[JvmContract[AnyRef]] =
    if !clazz.isAnnotationPresent(classOf[contract]) then None
    else if resolving.get().contains(clazz) then None
    else
      val previous = resolving.get()
      resolving.set(previous + clazz)
      try
        val nestedClazz = clazz.asInstanceOf[Class[AnyRef]]
        Some(
          if JvmContractDeriver.isRecordClass(clazz) then JvmContract.ofRecord(nestedClazz)
          else JvmContract.ofPrimary(nestedClazz)
        )
      finally
        resolving.set(previous)

  private def toNestedRaw(
    nested: JvmContract[AnyRef],
    clazz:  Class[?],
    value:  Any
  ): Option[RawObject] =
    value match
      case null => None
      case typed if clazz.isInstance(typed.asInstanceOf[AnyRef]) =>
        Some(JvmValueConversions.deepToScala(nested.toRaw(typed.asInstanceOf[AnyRef])).asInstanceOf[RawObject])
      case _: java.util.Map[?, ?] | _: scala.collection.Map[?, ?] =>
        Some(JvmValueConversions.deepToScala(value).asInstanceOf[RawObject])
      case _ => None

  private def toScalaSeq(value: Any): Option[List[Any]] =
    value match
      case null => None
      case l: java.util.List[?] => Some(l.asScala.toList.map(JvmValueConversions.deepToScala))
      case l: List[?] => Some(l.map(JvmValueConversions.deepToScala))
      case seq: Seq[?] => Some(seq.toList.map(JvmValueConversions.deepToScala))
      case _ => None

  private def prefix(basePath: FieldPath, violation: Violation): Violation =
    Violation(
      FieldPath(basePath.segments ::: violation.path.segments),
      violation.code,
      violation.message
    )
